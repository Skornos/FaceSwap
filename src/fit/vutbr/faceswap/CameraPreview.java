package fit.vutbr.faceswap;

import java.io.ByteArrayOutputStream;
import android.graphics.Shader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import fit.vutbr.faceswap.HeadPose.HeadPoseStatus;
import fit.vutbr.faceswap.CameraActivity;
import android.graphics.PorterDuff;

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
    
    private DetectionBasedTracker  		mNativeDetector;

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
    
    private List<FaceRect> 				faces;
    private List<FaceRect> 				faces_filtered;
    
    private Mat 						mGray, mRgba, mHsv;
    
    public Camshift						mCamshift;
    private HeadPose 					hp;
    
    private CamShifting 				cs;
    private TrackerType					mTracker = TrackerType.KALMAN;
    public boolean 						nextFrame = false;
    private RotatedRect 				face_box;
    
    private static final Scalar   		FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    private Context						mContext;
    
    private List<android.graphics.Point>	kalmanCenterArray = new ArrayList<android.graphics.Point>();
    
    private Bitmap						cageFace;
    
    private int[] 						rgb;
        
    private Paint						shaderPaint;
    private RectF 						kalmanRect;
    
    private Bitmap 						mBitmapCopy;
    private Canvas						mCanvas;
    
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
        //mEyesDetector = loadCascadeClassifier(R.raw.haarcascade_eye_tree_eyeglasses);
        
        //mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
                        
        // CamShift init
    	cs = new CamShifting();
    	// KLT init
    	hp = new HeadPose();
    	
    	// bitmap with preview data
		//mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
    	
        cageFace = BitmapFactory.decodeResource(getResources(), R.drawable.cage_face);
        
        shaderPaint = new Paint();
        shaderPaint.setAntiAlias(true);     
        
        kalmanRect = new RectF();
        faces = new ArrayList<FaceRect>();
        faces_filtered = new ArrayList<FaceRect>();
    }
        
    public void setCamera(Camera camera) {
    	mCamera = camera;
    }

    public void setSize(int height, int width) {
    	mHeight = height;
    	mWidth = width;
    			
    	mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);
    	mBitmapCopy = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);
    	rgb = new int[mHeight*mWidth];
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
            
            //mNativeDetector = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);
            
            
            
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
    private List<FaceRect> detectFaces() {
    	MatOfRect faces;
    	Rect[] facesArray;
    	
    	faces = new MatOfRect();
    	
	    if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            //mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }
	    	    
	    if (mFaceDetector != null) {
	    	mFaceDetector.detectMultiScale(mGray, faces, 1.2, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());	    
	    }
	    /*
	    if (mNativeDetector != null)
            mNativeDetector.detect(mGray, faces);
	    */
	    facesArray = faces.toArray();
	    faces.release();
	    
	    List<FaceRect> f = new ArrayList<FaceRect>();
	    
	    for (Rect face:facesArray)
	    	f.add(new FaceRect(face));
	    
	    return f;
    }
    
    
    
    /**
     * Kalman filter
     */
    private void kalmanFilter(int dt) {
    	nextFrame = false;
    	faces_filtered.clear();
    	List<FaceRect> facesArray_prev_frame = faces;
    	List<FaceRect> facesArray_curr_frame = detectFaces();
		
		// detekovany alespon 2 obliceje, 
		// Kalman filter pro kazdy => hungarian algorithm
		// Seradim obliceje
		if (facesArray_prev_frame != null && facesArray_curr_frame.size() > 1) {
			HungarianMatrix matrix = new HungarianMatrix(facesArray_prev_frame, facesArray_curr_frame);			
			facesArray_curr_frame = matrix.orderByAssociations(facesArray_curr_frame);	
		}
		
		int size_delta = faces.size() - facesArray_curr_frame.size();
		if (size_delta <= 0) {
		// add to *faces*
			int i = 0;
			for (FaceRect f:facesArray_curr_frame) {
				if (i < faces.size())
					f.setCounter(faces.get(i).getCounter());
				i++;
			}
			faces = facesArray_curr_frame;
		}
		else {
		// remove from *faces*
			faces.subList(facesArray_curr_frame.size(), faces.size()).clear();
		}
		
		for (FaceRect f:faces) { 
			f.incCounter();
			if (f.toShow())
				faces_filtered.add(f);
		}
				
		int[] centers = new int[faces_filtered.size()*2];
		int i = 0;
		for (FaceRect f:faces_filtered) {
			centers[i++] = (int)f.getRect().tl().x+f.getRect().width/2;
			centers[i++] = (int)f.getRect().tl().y+f.getRect().height/2;
		}
					
		int ret[] = new int[centers.length];
		// native call to Kalman Filter						
		
		ret = kalmanFilterNative(centers, dt);
		
		int j = 0;
		for (FaceRect f:faces_filtered) {
			f.setCenter(ret[j], ret[j+1]);
			j+=2;
		}
				
    }
    
    /**
     * Returns preview frame data of the camera
     */
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {  
		/*Camera.Size previewSize = mCamera.getParameters().getPreviewSize(); 
		YuvImage yuvimage=new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		yuvimage.compressToJpeg(new android.graphics.Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
		byte[] jdata = baos.toByteArray();
		
		// Convert to Bitmap
		Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
		*/
		
		
		/*
		// byte array to bitmap
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
	    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, getWidth(), getHeight(), null);
	    yuv.compressToJpeg(new android.graphics.Rect(0, 0, getWidth(), getHeight()), 100, stream);
	    mBitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
		Log.d(TAG, mBitmap+"");
		*/
		
		// prevod na rgb565 za 15ms
		
		
		decodeYUV420SPNative(rgb, data, mWidth, mHeight);
		mBitmap.setPixels(rgb, 0, mWidth, 0, 0, mWidth, mHeight);
		mBitmapCopy.setPixels(rgb, 0, mWidth, 0, 0, mWidth, mHeight);
		
		//extractLuminanceNative(data, width, height, width, height, rgb);
		//toRGB565(data, width, height, rgb);
	    // copyPixel by mlo byt rychlejsi nez setPixels, ale funguje divne
	    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
	    //mBitmap.setPixels(rgb, 0, getWidth(), 0, 0, getWidth(), getHeight());
	    //mBitmap.copyPixelsFromBuffer(rgbBuffer);
	    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
	    

		// framerate
		int dt = (int) (System.currentTimeMillis()-timestamp);
		Log.i("FaceDetector","Time Gap = "+dt);
		CameraActivity.fpsTextView.setText("FPS: " + (int)(1000/dt));
		timestamp=System.currentTimeMillis();
	    
	    // create OpenCV Mat from preview frame data
	    Mat mYuv = new Mat(mHeight + mHeight / 2, mWidth, CvType.CV_8UC1);
	    mYuv.put(0, 0, data);
	    
	    // convert YUV to grayscale
	    mGray = mYuv.submat(0, mHeight, 0, mWidth);
	    // convert YUV to RGBA
	    mRgba = new Mat(mHeight, mWidth, CvType.CV_8UC4);
		Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420p2RGBA, 4);
		
		mHsv = new Mat(mRgba.size(),CvType.CV_8UC3);
		Imgproc.cvtColor(mRgba, mHsv, Imgproc.COLOR_RGB2HSV, 3);				
		
		// tracker logic
	    switch (mTracker) {
	    	/*
		    case CAMSHIFT:
	    		camshiftTracking();
	    		break;
	    		
	    	case KLT:
	    		kltTracking();
	            break;
	    	*/
	    
	    	case KALMAN:
	    		kalmanFilter(dt);	    		
	    		
	    		break;
	        
	    	case NONE:
	    		// no tracker, just detect faces
	    		nextFrame = false;
	    		faces = detectFaces();
	    		
	    		break;
	    		
			default:
				break;
	    }
	    
	    mYuv.release();
	    mHsv.release();
	    mGray.release();
	    mRgba.release();
	    
	    invalidate();
	    mCamera.addCallbackBuffer(data);
		
	    return;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		Canvas canvasTmp = new Canvas(mBitmap);
		mCanvas = canvasTmp;
		
		// zrcadleni canvasu pro predni kameru
		if (CameraActivity.mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			//canvasTmp.scale(-1f, 1f, mWidth* 0.5f, mHeight* 0.5f);
			//canvas.scale(-1f, 1f, mWidth* 0.5f, mHeight* 0.5f);
		}
		
		if (faces != null) { 
			
			if (faces.size() > 0) {
				switch (mTracker) {
					/*
					case CAMSHIFT:			    		
			    		if (nextFrame && face_box!= null) {
					        Point[] pts = new Point[4];
					        face_box.points(pts);
					        canvasTmp.drawLine((float)pts[0].x, (float)pts[0].y, 
					        				(float)pts[1].x, (float)pts[1].y, 
					        				red_rectPaint);
					        
					        canvasTmp.drawLine((float)pts[1].x, (float)pts[1].y, 
							        		(float)pts[2].x, (float)pts[2].y, 
							        		red_rectPaint);
					        
					        canvasTmp.drawLine((float)pts[2].x, (float)pts[2].y, 
							        		(float)pts[3].x, (float)pts[3].y, 
							        		red_rectPaint);
					        
					        canvasTmp.drawLine((float)pts[3].x, (float)pts[3].y, 
							        		(float)pts[0].x, (float)pts[0].y, 
							        		red_rectPaint);
							
			    		}
			    		break;
			    		
			    	case KLT:
			    		if (hp.hpstatus == HeadPoseStatus.NONE){
			    			canvasTmp.drawRect((float)facesArray[0].tl().x, (float)facesArray[0].tl().y, 
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
								canvasTmp.drawCircle(cx, cy, 2, green_rectPaint);
							}
						}
			            
			            break;
		    		*/
					
			    	case KALMAN:
			    		int k = 0;
			    		for (FaceRect f:faces_filtered) {
			    			
			    			/* kruhovy index swap*/
			    			int index = 0;
			    			if (faces_filtered.size() >= 2)
			    				index = k == faces_filtered.size()-1 ? 0 : k+1;
			    			if (mBitmapCopy != null) {
				    			Bitmap faceBmp = Bitmap.createBitmap(
				    					mBitmapCopy, 
							    		(int)f.getRect().tl().x, 
							    		(int)f.getRect().tl().y, 
							    		(int)(f.getRect().br().x - f.getRect().tl().x), 
							    		(int)(f.getRect().br().y - f.getRect().tl().y)
							    	);
				    			faceBmp = Bitmap.createScaledBitmap(faceBmp, 
				    							faces_filtered.get(index).getRect().width, 
				    							faces_filtered.get(index).getRect().height, 
				    							false);
				    			
				    			canvasTmp.drawBitmap(getRoundedShape(faceBmp), 
				    					(float)faces_filtered.get(index).getRect().tl().x, 
				    					(float)faces_filtered.get(index).getRect().tl().y, 
				    					null);
			    			}
			    			k++;
			    			
			    			/*
			    			if (mBitmapCopy != null) {
			    				canvasTmp.drawText(k+"", f.getRect().x, f.getRect().y, textPaint);
	    						f.findMostSimilar();
	    						Log.d(TAG, "Most similar to: " + faces_filtered.indexOf(f) + " is: " + faces_filtered.indexOf(f.getMostSimilar()));
			    				
			    				FaceRect mostSimilar = f.getMostSimilar();
			    				
				    			Bitmap faceBmp = Bitmap.createBitmap(
				    					mBitmapCopy, 
							    		(int)mostSimilar.getRect().tl().x, 
							    		(int)mostSimilar.getRect().tl().y, 
							    		(int)(mostSimilar.getRect().br().x - mostSimilar.getRect().tl().x), 
							    		(int)(mostSimilar.getRect().br().y - mostSimilar.getRect().tl().y)
							    	);
				    			faceBmp = Bitmap.createScaledBitmap(faceBmp, 
				    							f.getRect().width, 
				    							f.getRect().height, 
				    							false);
				    			
				    			canvasTmp.drawBitmap(getRoundedShape(faceBmp), 
				    					(float)f.getRect().tl().x, 
				    					(float)f.getRect().tl().y, 
				    					null);
			    			}
			    			
			    			//canvasTmp.drawOval(f.getOval(), red_rectPaint);
			    			*/
			    		}
			    		
			    		
			    		if (faces.size()>= 2) {			    			
			    			/*
			    			RectF k0 = new RectF(
			    					(float)(kalmanCenterArray.get(0).x-facesArray_filtered.get(0).width/3), 
									  (float)(kalmanCenterArray.get(0).y-facesArray_filtered.get(0).height/2),
									  (float)(kalmanCenterArray.get(0).x+facesArray_filtered.get(0).width/3), 
									  (float)(kalmanCenterArray.get(0).y+facesArray_filtered.get(0).height/2)
			    					);
			    			RectF k1 = new RectF(
			    					(float)(kalmanCenterArray.get(1).x-facesArray_filtered.get(1).width/3), 
			    					(float)(kalmanCenterArray.get(1).y-facesArray_filtered.get(1).height/2),
			    					(float)(kalmanCenterArray.get(1).x+facesArray_filtered.get(1).width/3), 
			    					(float)(kalmanCenterArray.get(1).y+facesArray_filtered.get(1).height/2)
			    					);
			    			*/
			    			//shaderPaint.setShader(new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
			    			
			    			//canvas.drawOval(k0, shaderPaint);
			    			//canvas.drawOval(k1, shaderPaint);
			    		}
			    		
			    					    		
			    		break;
			        
			    	case NONE:
			    		for (FaceRect f : faces) {
				    		canvasTmp.drawOval(f.getOval(), green_rectPaint);
			    		}
			    		
			    		/* grabCut
			    		if (faces.size() == 2) {			    	        
			    			
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
			    		}			    		
			    		*/
					default:
						break;
			    }
						        
			}
		}
		
		canvas.drawBitmap(mBitmap, 0, 0, new Paint(Paint.DITHER_FLAG));		
	}
		
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
	    float x = event.getX();
	    float y = event.getY();
	    switch(event.getAction())
	    {
	        case MotionEvent.ACTION_DOWN:
	        //Check if the x and y position of the touch is inside the bitmap
	        	int k = 0;
	        	for (FaceRect face:faces_filtered) {		        	
		        	if( x < face.getRect().br().x && x > face.getRect().tl().x && y < face.getRect().br().y && y > face.getRect().tl().y )
			        {
			            Log.d(TAG, face+"");
			            mCanvas.drawOval(face.getOval(), red_rectPaint);
			        }
			        k++;
	        	}
	        	return true;
	    }
	    return false;
	}
	
	private void saveToSDCard() {
		File folder = new File(Environment.getExternalStorageDirectory()+"/folder/");
        if(!folder.exists()) folder.mkdirs();

        try {
            setDrawingCacheEnabled(true);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File captureDir = new File(Environment.getExternalStorageDirectory()+"/Faceswap/");
            captureDir.mkdirs();
            File captureFile = new File(captureDir, "capture"+timeStamp+".jpeg");
            FileOutputStream fos = new FileOutputStream(captureFile);
            Bitmap bitmap = getDrawingCache();
            bitmap.compress(CompressFormat.JPEG, 100, fos);
            Toast.makeText(mContext, "Saved to SD Card", Toast.LENGTH_SHORT).show();
            setDrawingCacheEnabled(false);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
        	Toast.makeText(mContext, "File not found", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (IOException e) {
        	Toast.makeText(mContext, "Unable to save to SD card", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
	}
	
	public void takePicture() {  
		// is autofocus supported?
		if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
			mCamera.autoFocus(new AutoFocusCallback() {
		         @Override
		         public void onAutoFocus(boolean success, Camera camera) {
		        	 Log.d(TAG, "Focusing");
		             if(success){
		            	 Log.d(TAG, "Focused");
		            	 saveToSDCard();
		             }
		         }
		     });
		}
		else
			saveToSDCard();
    	
    }
	
	public Bitmap getRoundedShape(Bitmap faceBmp) {
        int targetWidth = faceBmp.getWidth();
        int targetHeight = faceBmp.getHeight();
        
        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth,
                targetHeight, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(targetBitmap);
        Path path = new Path();
        
        Point center = new Point(faceBmp.getWidth()/2, faceBmp.getHeight()/2);
        RectF oval = new RectF((float)(center.x-faceBmp.getWidth()/3), 
				  (float)(center.y-faceBmp.getHeight()/2),
				  (float)(center.x+faceBmp.getWidth()/3), 
				  (float)(center.y+faceBmp.getHeight()/2));
        path.addOval(oval, Path.Direction.CCW);

        canvas.clipPath(path);
        Bitmap sourceBitmap = faceBmp;
        canvas.drawBitmap(
                sourceBitmap,
                (float)0.0, 
                (float)0.0, 
                null);
        
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
	
	class FaceRect {
		Rect 								r;
		int 								cnt;
		android.graphics.Point 				kalmanCenter;
		private static final int			MIN_DETECTED_FRAMES = 3;
		RectF 								oval;
		FaceRect							mostSimilar;
		
		FaceRect(Rect _r) {
			r = _r;
			cnt = 0;
			kalmanCenter = new android.graphics.Point();
			oval = new RectF();
		}
		
		public void setCenter(int x, int y) {
			kalmanCenter.set(x, y);
		}
		
		public android.graphics.Point getCenter() {
			return kalmanCenter;
		}
		
		public void setRect(Rect _r) {
			r = _r;
		}
		
		public Rect getRect() {
			return r;
		}
		
		public void incCounter() {
			cnt++;
		}
		
		public void setCounter(int _cnt) {
			cnt = _cnt;
		}
		
		public int getCounter() {
			return cnt;
		}
		
		public boolean toShow() {
			if (cnt >= MIN_DETECTED_FRAMES)
				return true;
			else
				return false;
		}
		
		public RectF getOval() {
			oval.set((float)(kalmanCenter.x-r.width/3), 
					  (float)(kalmanCenter.y-r.height/2),
					  (float)(kalmanCenter.x+r.width/3), 
					  (float)(kalmanCenter.y+r.height/2)
					);
			
			return oval;
		}
		
		public Mat getMat() {
			return mHsv.submat(getRect());
		}
		
		public void findMostSimilar() {
			double similarity_res = -1.0;
			double similarity_tmp;
			FaceRect mostSimilarTmp = this;
			
			for (FaceRect f:faces_filtered) {
				if (this != f) {
					similarity_tmp = compareHistNative(this.getMat().getNativeObjAddr(), f.getMat().getNativeObjAddr());
					Log.d(TAG, similarity_tmp + "");
					if (similarity_tmp > similarity_res) {
						similarity_res = similarity_tmp;
						mostSimilarTmp = f;
					}
				}
			}
			
			mostSimilar = mostSimilarTmp;
		}
		
		public FaceRect getMostSimilar() {
			return mostSimilar;
		}
		
	}
	
	
	
	private native int[] kalmanFilterNative(int[] centers, int dt);
	private native void decodeYUV420SPNative(int[] rgb, byte[] yuv420sp, int width, int height);
	
	private native double compareHistNative(long face0Addr, long face1Addr);
	
	private native void extractLuminanceNative(byte[] yuv, int yuv_width,
			int yuv_height, int rgb_width, int rgb_height, int[] rgb);
	
	private native void applyGrayScale(byte[] yuv, int yuv_width,
			int yuv_height, int[] rgb);
	
	private native void toRGB565(byte[] yuv, int width,
			int height, byte[] rgb);
	
	private native void processImage(byte[] data);
	
	/**
     * Detect eyes 
     */
    /*
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
    */
    
    /**
     * Camshift 
     */
    /*
    private void camshiftTracking() {
		
		// detect faces
		if(!nextFrame)
        {
        	//for (int i = 0; i < facearray1.length; i++)
             //Core.rectangle(mRgba, facearray1[i].tl(), facearray1[i].br(), FACE_RECT_COLOR, 3);
        	Log.i("FdView","Calling create tracked object");
        	facesArray = detectFaces();
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
        
    }
    */
    
    /**
     * KLT
     */
    /*
    private void kltTracking() {
    	nextFrame = false;	
		// detect faces
		if(hp.hpstatus==HeadPoseStatus.NONE) {
			facesArray = detectFaces();
    	}
		
		// track faces
        hp.hpFind(mRgba,mGray,hp,facesArray);
        if(hp.hpstatus==HeadPoseStatus.TRACKING) {
	        //Point center = new Point();
	        //int r = 4;
	        Log.i("HeadPose","FindCorner:Total Corners Found"+hp.features_next.total());
        }
    }
    */
	
	/**
	 * Task for asynchronous saving of byte array to JPEG
	 * Adapted from: stackoverflow...zdroj
	 *
	 */
	/*
	class SavePhotoTask extends AsyncTask<Void, String, String> {
	    @Override
	    protected String doInBackground(Void... context) {	    	
	    	File folder = new File(Environment.getExternalStorageDirectory()+"/folder/");
	        if(!folder.exists()) folder.mkdirs();

	        try {
	            setDrawingCacheEnabled(true);
	            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	            File captureDir = new File(Environment.getExternalStorageDirectory()+"/Faceswap/");
	            captureDir.mkdirs();
	            File captureFile = new File(captureDir, "capture"+timeStamp+".jpeg");
	            FileOutputStream fos = new FileOutputStream(captureFile);
	            Bitmap bitmap = getDrawingCache();
	            bitmap.compress(CompressFormat.JPEG, 100, fos);
	            //Toast.makeText(mContext, "Saved to SD Card", Toast.LENGTH_SHORT).show();
	            setDrawingCacheEnabled(false);
	            fos.flush();
	            fos.close();
	        } catch (FileNotFoundException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        } catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	        }
	      return(null);
	    }
	}
	*/
    
}
