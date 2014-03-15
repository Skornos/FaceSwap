package fit.vutbr.faceswap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import fit.vutbr.faceswap.HeadPose.HeadPoseStatus;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback,
														  Camera.PreviewCallback
{
	private static final String    		TAG = "CameraPreview";
	
	public enum 						TrackerType {NONE, CAMSHIFT, KLT, KALMAN};
	
	private SurfaceHolder 				mHolder;
    private Camera 						mCamera;
    private CascadeClassifier 			mJavaDetector;

	private int 						height, width;
	private float 						ratio, xRatio, yRatio;
	
    private Bitmap 						mBitmap;
    private long 						timestamp = 0;
    
    private int[] 						rgb;
    private IntBuffer 					rgbBuffer;
    
    private ShortBuffer 				rgb565Buffer;
    private short[] 					rgb565;
        
    private boolean 					listenerSupported;
    private MyFaceDetectionListener 	listener;
    
    private Paint 						textPaint;
    private Paint 						green_rectPaint;
    private Paint 						red_rectPaint;
    
    private float 						mRelativeFaceSize   = 0.2f;
    private int                   		mAbsoluteFaceSize   = 0;
    private Rect[] 						facesArray;
    
    private Mat 						mGray, mRgba;
    
    public Camshift						mCamshift;
    private HeadPose 					hp;
    
    private CamShifting 				cs;
    private TrackerType					mTracker = TrackerType.KALMAN;
    public boolean 						nextFrame = false;
    private RotatedRect 				face_box;
    
    private static final Scalar   		FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private Context						mContext;
    
    private android.graphics.Point		kalmanCenter;
    
    private Bitmap						cageFace;
    
    int cnt = 0;
    
    public CameraPreview(Context context) {
        super(context);
    }
    
    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @SuppressWarnings("deprecation")
	void setCameraPreview(Context context, Camera camera, CascadeClassifier javaDetector) {
        // super(context);
        mContext = context;
        
        Log.i(TAG, "CameraPreview created");
        
        textPaint = new Paint();
        textPaint.setARGB(255, 200, 0, 0);
		textPaint.setTextSize(40);
		
		green_rectPaint = new Paint();
		green_rectPaint.setColor(Color.GREEN);
		green_rectPaint.setStyle(Paint.Style.STROKE);
		green_rectPaint.setStrokeWidth(3);
		
		red_rectPaint = new Paint();
		red_rectPaint.setColor(Color.RED);
		red_rectPaint.setStyle(Paint.Style.STROKE);
		red_rectPaint.setStrokeWidth(3);
		
        mCamera = camera;
        mJavaDetector = javaDetector;
        
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setFormat(ImageFormat.NV21);
        // zastarale nastaveni, ale nutne pro Android verzi < 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                
        cageFace = BitmapFactory.decodeResource(getResources(), R.drawable.cage_face);
    }

    public void surfaceCreated(SurfaceHolder holder) {
    	Log.i(TAG, "Surface created");
    	
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            setWillNotDraw(false);
        } catch (IOException e) {
            Log.e(null, "Error setting camera preview: " + e.getMessage());
            mCamera.release();
        }
    }
    
    private void setCameraOptions() {
    	Parameters parameters = mCamera.getParameters();
    	
		height = parameters.getPreviewSize().height;
		width = parameters.getPreviewSize().width;
		for (Camera.Size res : parameters.getSupportedPreviewSizes()) {
			Log.d("Supported resolution", res.height + "x" + res.width);
			height = res.width;
			width = res.height;
		}
		List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
		Camera.Size cs = sizes.get(0);  

		height = cs.height;
		width = cs.width;

		mCamera.setParameters(parameters);
		Log.d("Setting", width + "x" + height);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {    	
    	setCameraOptions();
    	
    	/* API >= 14
    	if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
        	// podpora FaceDetectionListeneru
        	listenerSupported = true;
        	listener = new MyFaceDetectionListener(this);
        	mCamera.setFaceDetectionListener(listener);
        	mCamera.startFaceDetection();
        }
        else 
        	listenerSupported = false;
    	*/
    	
    	cs = new CamShifting();
    	hp = new HeadPose();
    	
		mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		
		rgb565 = new short[(int) (width*height)];
		rgb565Buffer = ShortBuffer.wrap(rgb565);
		
		rgb = new int[(int) (width * height * 2)];
		rgbBuffer = IntBuffer.wrap(rgb);
				
	    int bufSize = width * height *
	            ImageFormat.getBitsPerPixel(mCamera.getParameters().getPreviewFormat()) / 8;
	    
        mCamera.addCallbackBuffer(new byte[bufSize]);
        mCamera.setPreviewCallbackWithBuffer(this);
        
		return;
    }
    
    public void onPause() {

	    if (mCamera != null) {
	        mCamera.setPreviewCallback(null);
	        mCamera.stopPreview();
	        mCamera.release();
	    }
	    
	    setWillNotDraw(true);
	}
    
    private void detectFaces() {
    	MatOfRect faces;
    	
    	faces = new MatOfRect();
    	
	    if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }
	    	    
	    if (mJavaDetector != null) {
	    	mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());	    
	    }
	    
	    facesArray = faces.toArray();
	    faces.release();
    }
        
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {  
		
		/* byte array to bitmap
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
	    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);
	    yuv.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, stream);
		*/
		
		/* save byte array to internal storage
		new SavePhotoTask().execute(data);
		cnt++;
		*/
		
		
		// framerate
		Log.i("FaceDetector","Time Gap = "+(System.currentTimeMillis()-timestamp));
	    timestamp=System.currentTimeMillis();
	    
	    
	    Mat mYuv = new Mat(height + height / 2, width, CvType.CV_8UC1);
	    mYuv.put(0, 0, data);

	    mGray = mYuv.submat(0, height, 0, width);
	    
	    mRgba = new Mat(height, width, CvType.CV_8UC4);
		Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420p2RGBA, 4);
        
	    switch (mTracker) {
	    	case CAMSHIFT:
	    		/*
	    		if (mCamshift.obj.getTrackedFrames() >= mCamshift.obj.MAX_CAMSHIFT_FRAMES) {
	    			mCamshift.obj.setTrackedFrames(0);
	    		}
	    		*/
	    		
	    		if(!nextFrame)
	            {
	            	//for (int i = 0; i < facearray1.length; i++)
	                 //Core.rectangle(mRgba, facearray1[i].tl(), facearray1[i].br(), FACE_RECT_COLOR, 3);
	            	Log.i("FdView","Calling create tracked object");
	            	detectFaces();
	            	if (facesArray.length>0)
	            	{	
	            		cs.create_tracked_object(mRgba,facesArray,cs);
	            		nextFrame = true;
	            	}
	            }
	            
	            if(nextFrame)
	            {
	            	//track the face in the new frame
	            	Log.i("FdView","Tracking object");
	            	face_box = cs.camshift_track_face(mRgba,facesArray,cs);
	            	
	            	Point[] pts = new Point[4];
	            	face_box.points(pts);
	            	
	            	Core.line(mRgba, pts[0], pts[1], FACE_RECT_COLOR);
	            	Core.line(mRgba, pts[1], pts[2], FACE_RECT_COLOR);
	            	Core.line(mRgba, pts[2], pts[3], FACE_RECT_COLOR);
	            	Core.line(mRgba, pts[3], pts[0], FACE_RECT_COLOR);
	            	
	            	//mBitmap = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);
	            }
	    		
	            	
	            	//Core.ellipse(mRgba,face_box,FACE_RECT_COLOR, 6);
	            
	    		/*
	    		 * if (mCamshift.obj.active == false) {
	    			detectFaces();
	    			if (facesArray.length > 0) {
		    			//mCamshift.track(facesArray, mYuv);
		    				mCamshift.create_tracked_object(facesArray, mYuv);
	    		
	    			}
	    		}
	    		
	    		
	    		if (mCamshift.obj.active == true) {
	    			mCamshift.camshift_track_face(facesArray);
	    		}
	    		*/
	    		
	    		break;
	    		
	    	case KLT:
	    		nextFrame = false;
	    		if(hp.hpstatus==HeadPoseStatus.NONE) {
	    	    	detectFaces();
	        	}
	           
	            hp.hpFind(mRgba,mGray,hp,facesArray);
	            if(hp.hpstatus==HeadPoseStatus.TRACKING) {
	    	        //Point center = new Point();
	    	        //int r = 4;
	    	        Log.i("HeadPose","FindCorner:Total Corners Found"+hp.features_next.total());
	            }
	            
	            break;
	    	
	    	case KALMAN:
	    		nextFrame = false;
	    		detectFaces();
	    		
	    		if (facesArray.length>0) {
	    			int center_x = (int)facesArray[0].tl().x+facesArray[0].width/2;
	    			int center_y = (int)facesArray[0].tl().y+facesArray[0].height/2;
	    			
	    			//Log.d("orig. center", "x = " + center_x + ", y = " + center_y);
	    			
	    			int ret[] = new int[2];
	    			ret = kalmanFilter(center_x, center_y);
	    			
	    			kalmanCenter = new android.graphics.Point(ret[0], ret[1]);
	    			
	    			//Log.i("kalman center", "x = " + ret[0] + ", y = " + ret[1]);
	    		}
	    		
	    		break;
	        
	    	case NONE:
	    		nextFrame = false;
	    		detectFaces();
	    		
	    		break;
	    		
			default:
				break;
	    }
	    
	    mRgba.release();
	    mYuv.release();
	    mGray.release();
	    
	    invalidate();
	    mCamera.addCallbackBuffer(data);
		
	    return;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// zrcadleni canvasu pro predni kameru
		if (CameraActivity.mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT)
			canvas.scale(-1f, 1f, width* 0.5f, height* 0.5f);
		
		if (facesArray != null) { 
			/*
			if (facesArray.length > 1) {
				// 2 obliceje detekovany, muzu zamenit
				Bitmap croppedBitmap = Bitmap.createBitmap(
			    		mBitmap, 
			    		(int)facesArray[0].tl().x, 
			    		(int)facesArray[0].tl().y, 
			    		(int)(facesArray[0].br().x - facesArray[0].tl().x), 
			    		(int)(facesArray[0].br().y - facesArray[0].tl().y));
				
				Bitmap croppedBitmap2 = Bitmap.createBitmap(
			    		mBitmap, 
			    		(int)facesArray[1].tl().x, 
			    		(int)facesArray[1].tl().y, 
			    		(int)(facesArray[1].br().x - facesArray[1].tl().x), 
			    		(int)(facesArray[1].br().y - facesArray[1].tl().y));
				
				// faceswap mezi bitmapy
				//canvas.drawBitmap(croppedBitmap, (int)facesArray[1].tl().x, (int)facesArray[1].tl().y, null);
				//canvas.drawBitmap(croppedBitmap2, (int)facesArray[0].tl().x, (int)facesArray[0].tl().y, null);
				
				
			}
			*/
			
			if (facesArray.length > 0) {
				switch (mTracker) {
			    	case CAMSHIFT:			    		
			    		if (nextFrame && face_box!= null) {
					        Point[] pts = new Point[4];
					        face_box.points(pts);
					        canvas.drawLine((float)pts[0].x, (float)pts[0].y, 
					        				(float)pts[1].x, (float)pts[1].y, 
					        				red_rectPaint);
					        
					        canvas.drawLine((float)pts[1].x, (float)pts[1].y, 
							        		(float)pts[2].x, (float)pts[2].y, 
							        		red_rectPaint);
					        
					        canvas.drawLine((float)pts[2].x, (float)pts[2].y, 
							        		(float)pts[3].x, (float)pts[3].y, 
							        		red_rectPaint);
					        
					        canvas.drawLine((float)pts[3].x, (float)pts[3].y, 
							        		(float)pts[0].x, (float)pts[0].y, 
							        		red_rectPaint);
							
			    		}
			    		break;
			    		
			    	case KLT:
			    		if (hp.hpstatus == HeadPoseStatus.NONE){
			    			canvas.drawRect((float)facesArray[0].tl().x, (float)facesArray[0].tl().y, 
									(float)facesArray[0].br().x, (float)facesArray[0].br().y, 
									green_rectPaint);
			    		}
			    		
			    		if (hp.hpstatus == HeadPoseStatus.TRACKING) {
							for(int i = 0; i < hp.featurestracked.total(); i++ )
							{ 
								//center.x=facearray1[0].x + hp.corners.toList().get(i).x;
								//center.y=facearray1[0].y + hp.corners.toList().get(i).y;
								float cx=(float) (hp.TempFace[0].x+hp.featurestracked.toList().get(i).x);
								float cy=(float) (hp.TempFace[0].y+hp.featurestracked.toList().get(i).y);
								canvas.drawCircle(cx, cy, 2, green_rectPaint);
							}
						}
			            
			            break;
			    	
			    	case KALMAN:
			    		RectF rectfKalman = new RectF((float)(kalmanCenter.x-facesArray[0].width/3), 
			    									  (float)(kalmanCenter.y-facesArray[0].height/2),
			    									  (float)(kalmanCenter.x+facesArray[0].width/3), 
			    									  (float)(kalmanCenter.y+facesArray[0].height/2));
			    		//canvas.drawOval(rectfKalman, red_rectPaint);
			    		
			    		Bitmap scaledCageFace = Bitmap.createScaledBitmap(cageFace, (int)((float)facesArray[0].width * 2 / 3), facesArray[0].height, false);
			    		canvas.drawBitmap(scaledCageFace, kalmanCenter.x-facesArray[0].width/3, kalmanCenter.y-facesArray[0].height/2, null);
			    		
			    		break;
			        
			    	case NONE:
			    		RectF rectf = new RectF((float)facesArray[0].tl().x, (float)facesArray[0].tl().y, 
												(float)facesArray[0].br().x, (float)facesArray[0].br().y);
			    		canvas.drawOval(rectf, green_rectPaint);
			    		
					default:
						break;
			    }
						        
			}
		}
		//canvas.drawBitmap(mBitmap, 0, 0, new Paint(Paint.DITHER_FLAG));
		
		return;
	}
	
	public void setTracker(TrackerType tracker) {
		mTracker = tracker;
	}
	
	public TrackerType getTracker() {
		return mTracker;
	}
	
	class SavePhotoTask extends AsyncTask<byte[], String, String> {
	    @Override
	    protected String doInBackground(byte[]... jpeg) {
	      File photo = getOutputMediaFile();

	      if (photo.exists()) {
	            photo.delete();
	      }

	      try {
	        FileOutputStream fos=new FileOutputStream(photo.getPath());

	        fos.write(jpeg[0]);
	        fos.close();
	      }
	      catch (java.io.IOException e) {
	        Log.e("PictureDemo", "Exception in photoCallback", e);
	      }

	      return(null);
	    }
	    
	    /** Create a File for saving an image or video */
		private  File getOutputMediaFile(){
		    // To be safe, you should check that the SDCard is mounted
		    // using Environment.getExternalStorageState() before doing this. 
		    File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
		            + "/Android/data/"
		            + mContext.getPackageName()
		            + "/Files"); 

		    // This location works best if you want the created images to be shared
		    // between applications and persist after your app has been uninstalled.

		    // Create the storage directory if it does not exist
		    if (! mediaStorageDir.exists()){
		        if (! mediaStorageDir.mkdirs()){
		            return null;
		        }
		    } 
		    // Create a media file name
		    File mediaFile;
		    String mImageName="camshift"+ cnt +".jpg";
		    mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);  
		    
		    return mediaFile;
		} 
	}
	
	private native int[] kalmanFilter(int center_x, int center_y);
	
	private native void extractLuminanceNative(byte[] yuv, int yuv_width,
			int yuv_height, int rgb_width, int rgb_height, int[] rgb);
	
	private native void applyGrayScale(byte[] yuv, int yuv_width,
			int yuv_height, int[] rgb);
	
	private native void toRGB565Native(byte[] yuv, int width,
			int height, int[] rgb);
	
	private native void processImage(byte[] data);
    
	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		
	}
	
}
