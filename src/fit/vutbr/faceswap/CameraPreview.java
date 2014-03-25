package fit.vutbr.faceswap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import fit.vutbr.faceswap.HeadPose.HeadPoseStatus;

/**
 * SurfaceView implementing SurfaceHolder callback (callback for
 * surfaceDestroyed, surfaceCreated,...) and Camera Preview callback
 * (onPreviewFrame).
 *
 */
public class CameraPreview extends SurfaceView implements Camera.PreviewCallback
{
	private static final String    		TAG = "CameraPreview";
	
	public enum 						TrackerType {NONE, CAMSHIFT, KLT, KALMAN};
	
	private SurfaceHolder 				mHolder;
    private Camera 						mCamera;
    
    private CascadeClassifier 			mFaceDetector;
    private CascadeClassifier 			mEyesDetector;

	private int 						mHeight, mWidth;
	private float 						ratio, xRatio, yRatio;
	
    private Bitmap 						mBitmap;
    private long 						timestamp = 0;
        
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
    private TrackerType					mTracker = TrackerType.NONE;
    public boolean 						nextFrame = false;
    private RotatedRect 				face_box;
    
    private static final Scalar   		FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private Context						mContext;
    
    private android.graphics.Point		kalmanCenter;
    
    private Bitmap						cageFace;
    
    private int[] 						rgb;
    private IntBuffer 					rgbBuffer;
    
    private ShortBuffer 				rgb565Buffer;
    private short[] 					rgb565;
    int cnt = 0;
    
    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    CameraPreview(Context context) {
        super(context);
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
		
        mFaceDetector = loadCascadeClassifier(R.raw.lbpcascade_frontalface);
        mEyesDetector = loadCascadeClassifier(R.raw.haarcascade_eye_tree_eyeglasses);
                        
        // CamShift init
    	cs = new CamShifting();
    	// KLT init
    	hp = new HeadPose();
    	
    	// bitmap with preview data
		//mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
    	
        cageFace = BitmapFactory.decodeResource(getResources(), R.drawable.cage_face);
    }
        
    public void setCamera(Camera camera) {
    	mCamera = camera;
    }

    public void setSize(int height, int width) {
    	mHeight = height;
    	mWidth = width;
    }
    
    private CascadeClassifier loadCascadeClassifier(int id) {
    	CascadeClassifier detector = null;
    	File cascadeFile = null;
    	
    	try {
            // load cascade file from application resources
            InputStream is = mContext.getResources().openRawResource(id);
            File cascadeDir = mContext.getDir("cascade", Context.MODE_PRIVATE);
            
            if (id == R.raw.lbpcascade_frontalface) 
            	cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            else if (id == R.raw.haarcascade_eye_tree_eyeglasses)
            	cascadeFile = new File(cascadeDir, "haarcascade_eye_tree_eyeglasses.xml");
            
            FileOutputStream os = new FileOutputStream(cascadeFile);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            detector = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (detector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                detector = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());
            
            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    	
    	return detector;
    }

    
    public void onPause() {

	    if (mCamera != null) {
	        mCamera.setPreviewCallback(null);
	        mCamera.stopPreview();
	        mCamera.release();
	    }
	    
	    setWillNotDraw(true);
	}
    
    /**
     * Function to detect faces with Haar cascade in OpenCV
     */
    private void detectFaces() {
    	MatOfRect faces;
    	
    	faces = new MatOfRect();
    	
	    if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }
	    	    
	    if (mFaceDetector != null) {
	    	mFaceDetector.detectMultiScale(mGray, faces, 1.3, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());	    
	    }
	    
	    facesArray = faces.toArray();
	    faces.release();
    }
    
    private Rect[] detectEyes(Mat face) {
    	MatOfRect eyes;
    	Rect[] eyesArray;
    	
    	eyes = new MatOfRect();
	    	    
	    if (mEyesDetector != null) {
	    	// zmenseni o 20%
	    	mEyesDetector.detectMultiScale(face, eyes, 1.3, 2, 2, new Size(mAbsoluteFaceSize/8, mAbsoluteFaceSize/8), new Size());
	    }
	    
	    eyesArray = eyes.toArray();
	    eyes.release();
	    
	    return eyesArray;
    }
    
    /**
     * Camshift 
     */
    private void camshiftTracking() {
    	/*
		if (mCamshift.obj.getTrackedFrames() >= mCamshift.obj.MAX_CAMSHIFT_FRAMES) {
			mCamshift.obj.setTrackedFrames(0);
		}
		*/
		
		// detect faces
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
        
		// tracking faces
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
    }
    
    /**
     * KLT
     */
    private void kltTracking() {
    	nextFrame = false;	
		// detect faces
		if(hp.hpstatus==HeadPoseStatus.NONE) {
	    	detectFaces();
    	}
		
		// track faces
        hp.hpFind(mRgba,mGray,hp,facesArray);
        if(hp.hpstatus==HeadPoseStatus.TRACKING) {
	        //Point center = new Point();
	        //int r = 4;
	        Log.i("HeadPose","FindCorner:Total Corners Found"+hp.features_next.total());
        }
    }
    
    /**
     * Kalman filter
     */
    private void kalmanFilter() {
    	nextFrame = false;
		// detect faces
		detectFaces();
		
		if (facesArray.length>0) {
			// center of the detected face's frame
			int center_x = (int)facesArray[0].tl().x+facesArray[0].width/2;
			int center_y = (int)facesArray[0].tl().y+facesArray[0].height/2;
			
			//Log.d("orig. center", "x = " + center_x + ", y = " + center_y);
			
			int ret[] = new int[2];
			// native call to Kalman Filter
			ret = kalmanFilter(center_x, center_y);
			
			kalmanCenter = new android.graphics.Point(ret[0], ret[1]);
			
			//Log.i("kalman center", "x = " + ret[0] + ", y = " + ret[1]);
		}
    }
    
    /**
     * Returns preview frame data of the camera
     */
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
		
		// prevod na rgb565 za 15ms
		/*
	    //extractLuminanceNative(data, width, height, width, height, rgb);
		toRGB565(data, width, height, rgb);
	    // copyPixel by mlo byt rychlejsi nez setPixels, ale funguje divne
	    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
	    mBitmap.setPixels(rgb, 0, width, 0, 0, width, height);
	    //mBitmap.copyPixelsFromBuffer(rgbBuffer);
	    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
	    */
		
		// framerate
		Log.i("FaceDetector","Time Gap = "+(System.currentTimeMillis()-timestamp));
	    timestamp=System.currentTimeMillis();
	    
	    // create OpenCV Mat from preview frame data
	    Mat mYuv = new Mat(mHeight + mHeight / 2, mWidth, CvType.CV_8UC1);
	    mYuv.put(0, 0, data);
	    
	    // convert YUV to grayscale
	    mGray = mYuv.submat(0, mHeight, 0, mWidth);
	    // convert YUV to RGBA
	    mRgba = new Mat(mHeight, mWidth, CvType.CV_8UC4);
		Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420p2RGBA, 4);
				
		// tracker logic
	    switch (mTracker) {
	    	case CAMSHIFT:
	    		camshiftTracking();
	    		break;
	    		
	    	case KLT:
	    		kltTracking();
	            break;
	    	
	    	case KALMAN:
	    		kalmanFilter();
	    		break;
	        
	    	case NONE:
	    		// no tracker, just detect faces
	    		nextFrame = false;
	    		detectFaces();
	    		
	    		/*for (Rect face : facesArray) {
	    			Rect
	    		}*/
	    		
	    		break;
	    		
			default:
				break;
	    }
	    
	    mYuv.release();
	    mGray.release();
	    mRgba.release();
	    
	    invalidate();
	    mCamera.addCallbackBuffer(data);
		
	    return;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// zrcadleni canvasu pro predni kameru
		if (CameraActivity.mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT)
			canvas.scale(-1f, 1f, mWidth* 0.5f, mHeight* 0.5f);
		
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
			    		canvas.drawOval(rectfKalman, red_rectPaint);
			    		
			    		//Bitmap scaledCageFace = Bitmap.createScaledBitmap(cageFace, (int)((float)facesArray[0].width * 2 / 3), facesArray[0].height, false);
			    		//canvas.drawBitmap(scaledCageFace, kalmanCenter.x-facesArray[0].width/3, kalmanCenter.y-facesArray[0].height/2, null);
			    		
			    		break;
			        
			    	case NONE:
			    		for (Rect face : facesArray) {
				    		RectF rectf = new RectF((float)face.tl().x, (float)face.tl().y, 
													(float)face.br().x, (float)face.br().y);
				    		canvas.drawOval(rectf, green_rectPaint);
			    		}
			    		/*
			    		if (facesArray.length > 0) {
			    			Mat face = mGray.submat(facesArray[0]);
			    			//Log.d(TAG, mGray.dump());
			    			Rect[] eyes = detectEyes(face);
			    			Log.d(TAG, "Eyes: " + eyes.length);
			    		}*/
			    		
			    		if (facesArray.length == 2) {			    	        
			    			/*
			    			 Bitmap scaledFace0 = Bitmap.createBitmap(
										    		mBitmap, 
										    		(int)facesArray[0].tl().x, 
										    		(int)facesArray[0].tl().y, 
										    		(int)(facesArray[0].br().x - facesArray[0].tl().x), 
										    		(int)(facesArray[0].br().y - facesArray[0].tl().y)
										    	);
							*/
			    			//Bitmap scaledFace1 = Bitmap.createScaledBitmap(mBitmap, (int)((float)facesArray[1].width), facesArray[1].height, false);
			    			/*
			    			scaledFace0 = getRoundedShape(scaledFace0);
			    			canvas.drawBitmap(scaledFace0, (float)facesArray[1].tl().x, (float)facesArray[1].tl().y, null);
				    		*/
			    			/*
			    			Mat mask = new Mat();
			    			Bitmap croppedBitmap = Bitmap.createBitmap(
						    		mBitmap, 
						    		(int)facesArray[0].tl().x, 
						    		(int)facesArray[0].tl().y, 
						    		(int)(facesArray[0].br().x - facesArray[0].tl().x), 
						    		(int)(facesArray[0].br().y - facesArray[0].tl().y));
			    			Mat imgC3 = new Mat();  
			    		    Imgproc.cvtColor(mRgba, imgC3, Imgproc.COLOR_RGBA2RGB);
			    			Imgproc.grabCut(imgC3, mask, facesArray[0], new Mat(), new Mat(), 2, Imgproc.GC_INIT_WITH_RECT);
			    			*/
			    		}
			    		
			    		//mGray.release();
			    		
			    		
					default:
						break;
			    }
						        
			}
		}
		
		//canvas.drawBitmap(mBitmap, 0, 0, new Paint(Paint.DITHER_FLAG));
		
		return;
	}
	
	public Bitmap getRoundedShape(Bitmap scaleBitmapImage) {
        int targetWidth = scaleBitmapImage.getWidth();
        int targetHeight = scaleBitmapImage.getHeight();

        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(targetBitmap);
        Path path = new Path();
        path.addCircle(
                ((float) targetWidth - 1) / 2,
                ((float) targetHeight - 1) / 2,
                (Math.min(((float) targetWidth), ((float) targetHeight)) / 2),
                Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmap = scaleBitmapImage;
        canvas.drawBitmap(
                sourceBitmap,
                new android.graphics.Rect(0, 0, sourceBitmap.getWidth(), sourceBitmap
                        .getHeight()), 
                new android.graphics.Rect(0, 0, targetWidth,
                        targetHeight), 
                new Paint());
        return targetBitmap;
    }
	
	/**
	 * Change tracker mode
	 * @param tracker New tracker
	 */
	public void setTracker(TrackerType tracker) {
		mTracker = tracker;
	}
	
	/**
	 * Obtain current tracker mode
	 * @return current tracker mode
	 */
	public TrackerType getTracker() {
		return mTracker;
	}
	
	/**
	 * Task for asynchronous saving of byte array to JPEG
	 * Adapted from: stackoverflow...zdroj
	 *
	 */
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
	
	private native void toRGB565(byte[] yuv, int width,
			int height, int[] rgb);
	
	private native void processImage(byte[] data);
    
}
