package fit.vutbr.faceswap;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import fit.vutbr.faceswap.CameraPreview.TrackerType;

/**
 * 
 * @author Petr Skornok
 * FaceSwap 1.0
 * Android app for (almost) realtime face swapping
 * Features:
 * 		- switching front and back camera if available
 * 		- using different face detection and tracking algorithms
 * 		- capture a photo 
 * 		- swap faces between people on screen or 
 * 		  or betweeen you and couple of prepared characters (starring Nicolas Cage)
 * 		- capture a video?
 *
 * Ikonky byly vytvoreny pomoci Adroid Asset Studio, licence: http://creativecommons.org/licenses/by/3.0/
 */
public class CameraActivity extends Activity {
	
	final static String 		  TAG = "Faceswap";
	
    private Preview 			   mPreview;
    private Camera 				   mCamera;
    public static int	   	       mCameraID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private MenuItem			   kltItem, camshiftItem, kalmanItem, noTrackerItem; 
    public static TextView 		   fpsTextView;
    
    // OpenCV static initialization
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        	Log.d(TAG, "OpenCV init error");
        }
        else {
        	System.loadLibrary("processImage");
        	//System.loadLibrary("detection_based_tracker");
        }
    }
    
    // OpenCV loader callback
    private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
    	@Override
	    public void onManagerConnected(int status) {
	        switch (status) {
	            case LoaderCallbackInterface.SUCCESS:
	            {
	                Log.i(TAG, "OpenCV loaded successfully");
	
	            } break;
	            default:
	            {
	                super.onManagerConnected(status);
	            } break;
	        }
   		}
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // keep screen ON
        //getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        Log.i(TAG, "Trying to load OpenCV library");
        // OpenCV static initialization
        mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        
        // passing an argument to Activity containing information
        // which camera to open (FRONT/BACK)
        Bundle b = getIntent().getExtras();
        Log.i("Bundle", getIntent().getExtras()+"");
        if (b != null) {
        	mCameraID = b.getInt("switchTo");
        	Log.i("Bundle", "mCameraID = " + mCameraID);
        }
        else {
        	//mCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;
        	Log.i("Bundle", "mCameraID = NULL");
        }
        
        mPreview = new Preview(this);
        setContentView(R.layout.main);
        
        ImageButton switch_camera_btn = (ImageButton) findViewById(R.id.switch_camera_button);
        switch_camera_btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					switchCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
				}
				else {
					switchCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
				}
			}
		});
        
        ImageButton settings_btn = (ImageButton) findViewById(R.id.settings_button);
        settings_btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				openOptionsMenu();
			}
		});
        
        fpsTextView = (TextView) findViewById(R.id.fpsHolder);
        fpsTextView.setText("FPS");
        
        FrameLayout fl = (FrameLayout) findViewById(R.id.cameraPreview);
        fl.addView(mPreview);
    }
    
    @Override
    protected void onPause() {
        super.onPause();

        // Release the Camera because we don't need it when paused
        // and other activities might need to use it.
        if (mCamera != null) {
        	mCamera.setPreviewCallback(null);
        	mCamera.stopPreview();
        	mPreview.mCameraPreview.getHolder().removeCallback(mPreview);
            mCamera.release();
            mCamera = null;
        }
        
        mPreview.mCameraPreview.setWillNotDraw(true);
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        
        mCamera = getCameraInstance();
        mPreview.setCamera(mCamera);
    }
    
    /**
     * Function for switching the camera to @cameraID*/
    private void switchCamera(int cameraID) {
    	///if (Build.VERSION.SDK_INT >= 11) {
    	   // recreate();
    	//} else {
    	Log.i("Bundle", "Switching from " + mCameraID + " to " + cameraID);
	    Intent intent = getIntent();
	    intent.putExtra("switchTo", cameraID); 
	    finish();
	    
	    startActivity(intent);
    	//}
	}
    
    /** Function which returns instance of Camera */
	public static Camera getCameraInstance() {
	    Camera c = null;
	    try {
	        //c = getFrontFacingCamera();
	    	c = Camera.open(mCameraID);
	    }
	    catch (Exception e){
	        // kamera neni dostupna
	    }
	    return c;
	}
    
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        camshiftItem = menu.add("CAMshift");
        kltItem = menu.add("KLT");
        kalmanItem = menu.add("Kalman filter");
        noTrackerItem = menu.add("No tracker");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        
    	if (item == kltItem) {
    		mPreview.mCameraPreview.setTracker(TrackerType.KLT);
    	}
    	else if (item == camshiftItem) {
    		mPreview.mCameraPreview.setTracker(TrackerType.CAMSHIFT);
    	}
    	else if (item == kalmanItem) {
    		mPreview.mCameraPreview.setTracker(TrackerType.KALMAN);
    	}        
	    else if (item == noTrackerItem) {
	    	mPreview.mCameraPreview.setTracker(TrackerType.NONE);
	    }
    
        return true;
    }
    
    /**
     * Function to open front camera*/
    static Camera getFrontFacingCamera() throws NoSuchElementException {
    	Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    	for (int cameraIndex = 0; cameraIndex < Camera.getNumberOfCameras(); cameraIndex++) {
    	    Camera.getCameraInfo(cameraIndex, cameraInfo);
    	    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
    	        try {
    	            return Camera.open(cameraIndex);
    	        } 
    	        catch (RuntimeException e) {
    	            e.printStackTrace();
    	        }
    	    }
    	}
    	throw new NoSuchElementException("Can't find front camera.");
    }
    
}

//----------------------------------------------------------------------

/**
* A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
* to the surface. We need to center the SurfaceView because not all devices have cameras that
* support preview sizes at the same aspect ratio as the device's display.
* 
* Adapted from: Samples/android-14/ApiDemos/src/com/example/android/apis/graphics/CameraPreview.java
*/
class Preview extends ViewGroup implements SurfaceHolder.Callback {
 private final String TAG = "Preview";

 CameraPreview 		mCameraPreview;
 SurfaceHolder		mHolder;
 Size 				mPreviewSize;
 List<Size> 		mSupportedPreviewSizes;
 Camera 			mCamera;
 
 @SuppressWarnings("deprecation")
Preview(Context context) {
     super(context);
     
     /*
     ImageButton im = new ImageButton(context);
     im.setImageResource(R.drawable.switch_100px_white);
     im.setBackground(null);
     im.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mCameraID == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					switchCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
				}
				else {
					switchCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
				}
			}
	 });   
     addView(im);*/
     
     mCameraPreview = new CameraPreview(context);
     addView(mCameraPreview);

     // Install a SurfaceHolder.Callback so we get notified when the
     // underlying surface is created and destroyed.
     mHolder = mCameraPreview.getHolder();
     mHolder.addCallback(this);
     mHolder.setFormat(ImageFormat.NV21);
     mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
 }

 public void setCamera(Camera camera) {
     mCamera = camera;
     mCameraPreview.setCamera(camera);
     if (mCamera != null) {
         mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
         requestLayout();
     }
 }

 public void switchCamera(Camera camera) {
    setCamera(camera);
    
    try {
        camera.setPreviewDisplay(mHolder);
    } catch (IOException exception) {
        Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
    }
    Camera.Parameters parameters = camera.getParameters();
    parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
    requestLayout();

    camera.setParameters(parameters);
 }

 @Override
 protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
     // We purposely disregard child measurements because act as a
     // wrapper to a SurfaceView that centers the camera preview instead
     // of stretching it.
     final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
     final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
     setMeasuredDimension(width, height);

     if (mSupportedPreviewSizes != null) {
         mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
     }
 }

 @Override
 protected void onLayout(boolean changed, int l, int t, int r, int b) {
     if (changed && getChildCount() > 0) {
         final View child = getChildAt(0);

         final int width = r - l;
         final int height = b - t;

         int previewWidth = width;
         int previewHeight = height;
         if (mPreviewSize != null) {
             previewWidth = mPreviewSize.width;
             previewHeight = mPreviewSize.height;
         }

         // Center the child SurfaceView within the parent.
         if (width * previewHeight > height * previewWidth) {
             final int scaledChildWidth = previewWidth * height / previewHeight;
             child.layout((width - scaledChildWidth) / 2, 0,
                     (width + scaledChildWidth) / 2, height);
         } else {
             final int scaledChildHeight = previewHeight * width / previewWidth;
             child.layout(0, (height - scaledChildHeight) / 2,
                     width, (height + scaledChildHeight) / 2);
         }
     }
 }

 public void surfaceCreated(SurfaceHolder holder) {
     // The Surface has been created, acquire the camera and tell it where
     // to draw.
     try {
         if (mCamera != null) {
             mCamera.setPreviewDisplay(holder);
         }
     } catch (IOException exception) {
         Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
     }
     
     mCameraPreview.setWillNotDraw(false);
 }

 public void surfaceDestroyed(SurfaceHolder holder) {
     // Surface will be destroyed when we return, so stop the preview.
     if (mCamera != null) {
        // mCamera.stopPreview();
     }
 }


 private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
     final double ASPECT_TOLERANCE = 0.1;
     double targetRatio = (double) w / h;
     if (sizes == null) return null;

     Size optimalSize = null;
     double minDiff = Double.MAX_VALUE;

     int targetHeight = h;

     // Try to find an size match aspect ratio and size
     for (Size size : sizes) {
         double ratio = (double) size.width / size.height;
         if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
         if (Math.abs(size.height - targetHeight) < minDiff) {
             optimalSize = size;
             minDiff = Math.abs(size.height - targetHeight);
         }
     }

     // Cannot find the one match the aspect ratio, ignore the requirement
     if (optimalSize == null) {
         minDiff = Double.MAX_VALUE;
         for (Size size : sizes) {
             if (Math.abs(size.height - targetHeight) < minDiff) {
                 optimalSize = size;
                 minDiff = Math.abs(size.height - targetHeight);
             }
         }
     }
     return optimalSize;
 }

 public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
     // Now that the size is known, set up the camera parameters and begin
     // the preview.
     Camera.Parameters parameters = mCamera.getParameters();
     parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
     requestLayout();

     mCamera.setParameters(parameters);
     
     int height = parameters.getPreviewSize().height;
	 int width = parameters.getPreviewSize().width;
	 
	 mCameraPreview.setSize(height, width);
	 
    // using preview callback buffer which is faster than normal callback			
    int bufSize = height * width *
            ImageFormat.getBitsPerPixel(mCamera.getParameters().getPreviewFormat()) / 8;
    
    mCamera.addCallbackBuffer(new byte[bufSize]);
    mCamera.addCallbackBuffer(new byte[bufSize]);
	
    mCamera.setPreviewCallbackWithBuffer(mCameraPreview);
    //mCamera.setPreviewCallback(mCameraPreview);
    mCamera.startPreview();
    
    /*
    TextView fpsTextView = (TextView) findViewById(R.id.fpsHolder);
    if (fpsTextView == null) {
    	Log.e(TAG, "FPS TEXT NOT FOUND FROM PREVIEW");
    	System.exit(1);
    }
    else
    	Log.d(TAG, "fpsTextView: " + fpsTextView);
    	*/
    
     
 }

}