package fit.vutbr.faceswap;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.util.Log;
import android.view.SurfaceHolder;

public class MyFaceDetectionListener implements Camera.FaceDetectionListener {
	CameraPreview mPreview;
	Face[] mFaces;
	
	public MyFaceDetectionListener(CameraPreview preview) {
		mPreview = preview;
	}
	
    @Override
    public void onFaceDetection(Face[] faces, Camera camera) {
    	mFaces = faces;
        mPreview.invalidate();
        if (faces.length > 0){
            Log.d("FaceDetection", "face detected: "+ faces.length +
                    " Face 1 Location X: " + faces[0].rect.centerX() +
                    "Y: " + faces[0].rect.centerY() );
        }
        
    }
}