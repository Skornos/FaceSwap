package fit.vutbr.faceswap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import fit.vutbr.faceswap.HeadPose.HeadPoseStatus;
import fit.vutbr.faceswap.CameraActivity;

/**
 * SurfaceView implementing SurfaceHolder callback (callback for
 * surfaceDestroyed, surfaceCreated,...) and Camera Preview callback
 * (onPreviewFrame).
 *
 */
public class CameraPreview extends SurfaceView implements Camera.PreviewCallback
{
	private static final String    		TAG = "CameraPreview";
	private static final int			MIN_DETECTED_FRAMES = 7;
	
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
    
    private Rect[] 						facesArray;
    private List<Rect> 					facesArray_filtered;
    private List<Integer>				detectedCounter = new ArrayList<Integer>();;
    
    private Mat 						mGray, mRgba;
    
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
        //mEyesDetector = loadCascadeClassifier(R.raw.haarcascade_eye_tree_eyeglasses);
        
        //mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
                        
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
    private Rect[] detectFaces() {
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
	    	mFaceDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());	    
	    }
	    /*
	    if (mNativeDetector != null)
            mNativeDetector.detect(mGray, faces);
	    */
	    facesArray = faces.toArray();
	    faces.release();
	    
	    return facesArray;
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
    
    /**
     * Kalman filter
     */
    private void kalmanFilter() {
    	nextFrame = false;
    	
    	// detect faces
    	Rect[] facesArray_prev_frame = facesArray;
		Rect[] facesArray_curr_frame = detectFaces();
		
		// detekovany alespon 2 obliceje, 
		// Kalman filter pro kazdy => hungarian algorithm
		// Seradim obliceje
		if (facesArray_prev_frame != null && facesArray_curr_frame.length > 1) {
			HungarianMatrix matrix = new HungarianMatrix(facesArray_prev_frame, facesArray_curr_frame);			
			facesArray = matrix.orderByAssociations(facesArray_curr_frame);			
		}
		else
			facesArray = facesArray_curr_frame;
    	
		// prenastavim velikost seznamu podle delky facesArray
		if (detectedCounter.size() < facesArray.length) {
			for (int i=0;i<facesArray.length;i++)
				detectedCounter.add(0);
		}
		else {
			detectedCounter.subList(facesArray.length, detectedCounter.size()).clear();
		}
		
		// vytvoreni seznamu obliceju, ktere se uz budou vykreslovat
		facesArray_filtered = new ArrayList<Rect>();
		for (int i=0; i<detectedCounter.size(); i++) {
			detectedCounter.set(i, detectedCounter.get(i)+1);
			if (detectedCounter.get(i) > MIN_DETECTED_FRAMES) {
				facesArray_filtered.add(facesArray[i]);
			}
			System.out.println("Detected at index " + i + ": " + detectedCounter.get(i));
		}
		
		int[] centers = new int[facesArray_filtered.size()*2];
		int i = 0;
		for (Rect face:facesArray_filtered) {
			centers[i++] = (int)face.tl().x+face.width/2;
			centers[i++] = (int)face.tl().y+face.height/2;
		}
					
		int ret[] = new int[facesArray.length*2];
		// native call to Kalman Filter						
		
		ret = kalmanFilterNative(centers);
		
		kalmanCenterArray.removeAll(kalmanCenterArray);
		for (int j=0; j < ret.length; j+=2) {
			android.graphics.Point kalmanCenter = new android.graphics.Point(ret[j], ret[j+1]);
			kalmanCenterArray.add(kalmanCenter);
		}
    	
    	/*
		// detect faces
    	Rect[] facesArray_prev_frame = facesArray;
		Rect[] facesArray_curr_frame = detectFaces();
		
		// detekovany alespon 2 obliceje, 
		// Kalman filter pro kazdy => hungarian algorithm
		if (facesArray_prev_frame != null && facesArray_curr_frame.length > 1) {
			HungarianMatrix matrix = new HungarianMatrix(facesArray_prev_frame, facesArray_curr_frame);
			
			
			//for (Rect face:facesArray_curr_frame)
			//	Log.d(TAG, face.toString());
			
			
			facesArray = matrix.orderByAssociations(facesArray_curr_frame);
			
			
			//Log.d(TAG, " \n");
			//for (Rect face:facesArray)
			//	Log.d(TAG, face.toString());
			
			
			int center_x = (int)facesArray[0].tl().x+facesArray[0].width/2;
			int center_y = (int)facesArray[0].tl().y+facesArray[0].height/2;
			
			int center_x2 = (int)facesArray[1].tl().x+facesArray[1].width/2;
			int center_y2 = (int)facesArray[1].tl().y+facesArray[1].height/2;
			
			//Log.d("orig. center", "x = " + center_x + ", y = " + center_y);
			
			int ret[] = new int[4];
			// native call to Kalman Filter
			ret = kalmanFilterNative(center_x, center_y, center_x2, center_y2);
			
			Log.d("Kalman input", center_x + " " + center_y);
			Log.d("Kalman input", center_x2 + " " + center_y2);
			
			kalmanCenter = new android.graphics.Point(ret[0], ret[1]);
			kalmanCenter2 = new android.graphics.Point(ret[2], ret[3]);
			
			Log.d("Kalman output", kalmanCenter.x + " " + kalmanCenter.y);
			Log.d("Kalman output", kalmanCenter2.x + " " + kalmanCenter2.y);
			
		}
		else
			facesArray = facesArray_curr_frame;
		*/
    	
		/*
    	facesArray = detectFaces();
		if (facesArray.length>0) {
			// center of the detected face's frame
			int center_x = (int)facesArray[0].tl().x+facesArray[0].width/2;
			int center_y = (int)facesArray[0].tl().y+facesArray[0].height/2;
			
			//Log.d("orig. center", "x = " + center_x + ", y = " + center_y);
			
			int ret[] = new int[2];
			// native call to Kalman Filter
			ret = kalmanFilterNative(center_x, center_y);
			
			kalmanCenter = new android.graphics.Point(ret[0], ret[1]);
		}
		*/
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
		CameraActivity.fpsTextView.setText("FPS: " + (int)(1000/(System.currentTimeMillis()-timestamp)));
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
	    		facesArray = detectFaces();
	    		
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
			    		for (android.graphics.Point kalmanCenter:kalmanCenterArray) {
			    			RectF rectfKalman = new RectF((float)(kalmanCenter.x-facesArray[0].width/3), 
									  (float)(kalmanCenter.y-facesArray[0].height/2),
									  (float)(kalmanCenter.x+facesArray[0].width/3), 
									  (float)(kalmanCenter.y+facesArray[0].height/2));
			    			canvas.drawOval(rectfKalman, red_rectPaint);
			    		}
			    		
			    		/*
			    		if (facesArray.length == 2) {			    	        
			    			
			    			 Bitmap scaledFace0 = Bitmap.createBitmap(
										    		mBitmap, 
										    		(int)facesArray[0].tl().x, 
										    		(int)facesArray[0].tl().y, 
										    		(int)(facesArray[0].br().x - facesArray[0].tl().x), 
										    		(int)(facesArray[0].br().y - facesArray[0].tl().y)
										    	);
							
			    			//Bitmap scaledFace1 = Bitmap.createScaledBitmap(mBitmap, (int)((float)facesArray[1].width), facesArray[1].height, false);
			    			
			    			scaledFace0 = getRoundedShape(scaledFace0);
			    			canvas.drawBitmap(scaledFace0, (float)facesArray[1].tl().x, (float)facesArray[1].tl().y, null);
				    		
			    			
			    		}
			    		*/
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
			    		}
			    		*/
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
		
		//new SavePhotoTask().execute(canvas);
		//cnt++;
		
		
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
	class SavePhotoTask extends AsyncTask<Canvas, String, String> {
	    @Override
	    protected String doInBackground(Canvas... jpeg) {
	      File photo = getOutputMediaFile();

	      if (photo.exists()) {
	            photo.delete();
	      }

	      try {
	        FileOutputStream fos=new FileOutputStream(photo.getPath());

	        try {
	        	setDrawingCacheEnabled(true);
	        	//buildDrawingCache(true);
                getDrawingCache().compress(Bitmap.CompressFormat.JPEG, 100, fos);
                setDrawingCacheEnabled(false);
            } catch (Exception e) {
                Log.e("Error--------->", e.toString());
            }
	        
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
		    String mImageName="img"+ cnt +".jpg";
		    mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);  
		    
		    return mediaFile;
		} 
	}
	
	private native int[] kalmanFilterNative(int[] centers);
		
	private native void extractLuminanceNative(byte[] yuv, int yuv_width,
			int yuv_height, int rgb_width, int rgb_height, int[] rgb);
	
	private native void applyGrayScale(byte[] yuv, int yuv_width,
			int yuv_height, int[] rgb);
	
	private native void toRGB565(byte[] yuv, int width,
			int height, int[] rgb);
	
	private native void processImage(byte[] data);
    
}
