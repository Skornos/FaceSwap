package fit.vutbr.faceswap;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Menu;
import android.widget.FrameLayout;
import android.widget.Toast;

public class CameraActivity extends Activity {
	
    private Preview mPreview;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        Load();
    }
    
    public void Load(){
        Camera mCamera = getCameraInstance();
        if (mCamera != null){
            mPreview = new Preview(this, mCamera);
            setContentView(mPreview);
        }
        else {
           Toast toast = Toast.makeText(getApplicationContext(), 
              "Unable to find camera. Closing.", Toast.LENGTH_SHORT);
           toast.show();
           finish();
        }
    }
    
    /** Funkce, ktera vraci instanci Camera objektu */
	public static Camera getCameraInstance(){
	    Camera c = null;
	    try {
	        c = Camera.open(); 
	    }
	    catch (Exception e){
	        // kamera neni dostupna
	    }
	    return c;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}