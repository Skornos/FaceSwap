package fit.vutbr.faceswap;

import java.util.List;

import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

//----------------------------------------------------------------------

/**
* A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
* to the surface. We need to center the SurfaceView because not all devices have cameras that
* support preview sizes at the same aspect ratio as the device's display.
*/
public class Preview extends ViewGroup {
 //private final String TAG = "Preview";

 SurfaceView mSurfaceView;
 Size mPreviewSize;
 List<Size> mSupportedPreviewSizes;
 Camera mCamera;
 CameraPreview mCameraPreview;


 
 public Preview(Context context) {
     super(context);
 }
 
 public Preview(Context context, AttributeSet attrs) {
     super(context, attrs);
 }

 public Preview(Context context, AttributeSet attrs, int defStyle) {
     super(context, attrs, defStyle);
 }
 
 void setPreview(Context context, Camera camera, CascadeClassifier mJavaDetector) {
    // super(context);
          
     mCamera = camera;
     
     mCameraPreview = (CameraPreview) findViewById(R.id.camerapreview);
     mCameraPreview.setCameraPreview(context, mCamera, mJavaDetector);
     
     mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
     requestLayout();
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

}