package fit.vutbr.faceswap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.CascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;
import fit.vutbr.faceswap.CameraPreview.TrackerType;


public class CameraActivity extends Activity {
	
	final static String TAG = "Faceswap";
	
    private Preview 			   mPreview;
    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;
    private Camera 				   mCamera;
    public static int	   	       mCameraID = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private MenuItem			   kltItem, camshiftItem, kalmanItem, noTrackerItem; 
    
    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        	Log.d(TAG, "OpenCV init error");
        }
        else {
        	System.loadLibrary("processImage");
        }
    }
    
    private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
    	@Override
	    public void onManagerConnected(int status) {
	        switch (status) {
	            case LoaderCallbackInterface.SUCCESS:
	            {
	                Log.i(TAG, "OpenCV loaded successfully");
	                
	                
	                try {
	                    // load cascade file from application resources
	                    InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
	                    File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
	                    mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
	                    FileOutputStream os = new FileOutputStream(mCascadeFile);
	                    
	                    byte[] buffer = new byte[4096];
	                    int bytesRead;
	                    while ((bytesRead = is.read(buffer)) != -1) {
	                        os.write(buffer, 0, bytesRead);
	                    }
	                    is.close();
	                    os.close();
	
	                    mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
	                    if (mJavaDetector.empty()) {
	                        Log.e(TAG, "Failed to load cascade classifier");
	                        mJavaDetector = null;
	                    } else
	                        Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
	                    
	                    cascadeDir.delete();
	
	                } catch (IOException e) {
	                    e.printStackTrace();
	                    Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
	                }
	
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
    	Log.i(TAG, "onCreate: " + savedInstanceState);
        super.onCreate(savedInstanceState);
        
        // keep screen ON
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        Log.i(TAG, "Trying to load OpenCV library");
        // OpenCV static initialization
        mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        
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
        
        Load();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null){
        	mPreview.mCameraPreview.onPause();
        	mPreview.mCameraPreview.getHolder().removeCallback(mPreview.mCameraPreview);
        	mPreview = null;
        }
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        //Load();
    }
    
    public void Load(){
        mCamera = getCameraInstance();
        if (mCamera != null){
        	setContentView(R.layout.main);    	   	
    	   	mPreview = (Preview) findViewById(R.id.preview);
        	mPreview.setPreview(this, mCamera, mJavaDetector); 
        	
        	ImageButton switchCameraButton = (ImageButton) findViewById(R.id.switch_camera_button);
        	switchCameraButton.setOnClickListener(new OnClickListener() {
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
        }
        else {
           Toast toast = Toast.makeText(getApplicationContext(), 
              "Unable to find camera. Closing.", Toast.LENGTH_SHORT);
           toast.show();
           finish();
        }
    }
    
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
    
    /** Funkce, ktera vraci instanci Camera objektu */
	public static Camera getCameraInstance() {
	    Camera c = null;
	    try {
	        //c = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT); 
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