package fit.vutbr.faceswap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.security.InvalidParameterException;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback,
														  Camera.PreviewCallback
{
	
	private SurfaceHolder mHolder;
    private Camera mCamera;
    
	private int height, width;
	private float ratio, xRatio, yRatio;
	
    private Bitmap mBitmap;
    private long timestamp = 0;
    
    private int[] rgb;
    private IntBuffer rgbBuffer;
    
    private ShortBuffer rgb565Buffer;
    private short[] rgb565;
    
    private int foundFaces;
    private FaceDetector.Face[] mFaces;
    private FaceDetector faceDetector;
    private int maxFaces = 5;
    
    private boolean listenerSupported;
    private MyFaceDetectionListener listener;
    
    private Paint textPaint = new Paint();
    private Paint rectPaint = new Paint();
    
    
    public CameraPreview(Context context) {
        super(context);
    }
    
    @SuppressWarnings("deprecation")
	public CameraPreview(Context context, Camera camera) {
        super(context);
        
        textPaint.setARGB(255, 200, 0, 0);
		textPaint.setTextSize(40);
		
		rectPaint.setColor(Color.GREEN);
		rectPaint.setStyle(Paint.Style.STROKE);
		rectPaint.setStrokeWidth(3);
		
        mCamera = camera;
        
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setFormat(ImageFormat.NV21);
        // zastarale nastaveni, ale nutne pro Android verzi < 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
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

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
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
		//parameters.setPreviewSize(cs.width, cs.height);
		height = cs.height;
		width = cs.width;
		mCamera.setParameters(parameters);
		//height = 320;
		//width = 480;
		Log.d("Setting", width + "x" + height);
		//parameters.setPreviewSize(width, height);
		//mCamera.setParameters(parameters);
		mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		
		rgb565 = new short[(int) (width*height)];
		rgb565Buffer = ShortBuffer.wrap(rgb565);
		
		rgb = new int[(int) (width * height * 2)];
		rgbBuffer = IntBuffer.wrap(rgb);
		
		// FaceDetector inicializace
		mFaces = new FaceDetector.Face[maxFaces];
	    faceDetector = new FaceDetector(width, height, maxFaces);
		
	    int bufSize = width * height *
	            ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
	    
	    
        mCamera.addCallbackBuffer(new byte[bufSize]);
        mCamera.setPreviewCallbackWithBuffer(this);
        
        if (mCamera.getParameters().getMaxNumDetectedFaces() > 0) {
        	// podpora FaceDetectionListeneru
        	listenerSupported = true;
        	listener = new MyFaceDetectionListener(this);
        	mCamera.setFaceDetectionListener(listener);
        	mCamera.startFaceDetection();
        }
        else 
        	listenerSupported = false;
       // this.setX(40);
        
        // center surfaceview
        
        
		return;
    }
    
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		Log.i("FaceDetector","Time Gap = "+(System.currentTimeMillis()-timestamp));
	    timestamp=System.currentTimeMillis();
		if (!listenerSupported) {
		    // prevod na rgb565 za 15ms
		    extractLuminanceNative(data, width, height, width, height, rgb);
		    // copyPixel by mlo byt rychlejsi nez setPixels, ale funguje divne
		    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
		    mBitmap.setPixels(rgb, 0, width, 0, 0, width, height);
		    //toRGB565(data, width, height, rgb);
		    //mBitmap.copyPixelsFromBuffer(rgbBuffer);
		    //mBitmap.copyPixelsFromBuffer(rgb565Buffer);
		    
		    
		    /*
			    // prevod na bitmapu za cca 150ms, spolehlivejsi
			    ByteArrayOutputStream stream = new ByteArrayOutputStream();
			    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);
			    yuv.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
		
			    // zmena formatu obrazku z ARGB_8888 na RGB_565, podle dokumentace
			    // viz. http://developer.android.com/reference/android/media/FaceDetector.html#findFaces  
			    BitmapFactory.Options opts = new BitmapFactory.Options();
			    opts.inPreferredConfig = Config.RGB_565;
			    mBitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size(), opts);
			*/
			
		    // vyhledani obliceje za cca 500-1000ms
		    foundFaces = faceDetector.findFaces(mBitmap, mFaces);
		    invalidate();
		    mCamera.addCallbackBuffer(data);
		}
		
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
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		/*Paint rectPaint2 = new Paint();
		rectPaint2.setStyle(Paint.Style.STROKE);
		rectPaint2.setStrokeWidth(3);
		rectPaint2.setColor(Color.RED);
		canvas.drawRect(new Rect(20, 20, this.getWidth() - 20, this.getHeight() - 20), rectPaint2);*/
		if (mBitmap != null) {
			//canvas.drawText(this.foundFaces+"", 50, 50, textPaint);
	       //canvas.drawRect(new Rect(20, 20, mBitmap.getWidth() - 20, mBitmap.getHeight() - 20), rectPaint);
			
			if (listenerSupported && listener.mFaces != null) {
				Log.d("Found faces", listener.mFaces.length+"");
				
				if (listener.mFaces.length > 0) {
					Camera.Face face = listener.mFaces[0];
					canvas.drawCircle(face.leftEye.x, face.leftEye.y, 10, rectPaint);
				}
			}
	        //Log.d("Bitmap", mBitmap.getWidth() + "x" + mBitmap.getHeight());
	        //Log.d("Preview", this.getWidth() + "x" + this.getHeight());
			for (int i=0; i < foundFaces; i++) {
				/*Face face = mFaces[i];
		    	
		    	PointF midEyes = new PointF();
		        face.getMidPoint( midEyes );
		        
		        float eyedist = face.eyesDistance();
		        PointF lt = new PointF( midEyes.x - eyedist * 2.0f, midEyes.y - eyedist * 2.5f );
		        
		        canvas.drawRect(Math.max( (int) ( lt.x ), 0 ), 
		        				Math.max( (int) ( lt.y ), 0 ), 
		        				Math.min( (int) ( lt.x + eyedist * 4.0f ), getWidth() ), 
		        				Math.min( (int) ( lt.y + eyedist * 5.5f ), getHeight() ), 
		        				rectPaint);
		        				*/
				Face face = mFaces[i];
	            PointF midPoint=new PointF();
	            face.getMidPoint(midPoint);
	            
	            canvas.drawCircle(midPoint.x, midPoint.y, 10, rectPaint);
	            float eyeDistance=face.eyesDistance();
	            canvas.drawRect(midPoint.x-eyeDistance, midPoint.y-eyeDistance, midPoint.x+eyeDistance, midPoint.y+eyeDistance, rectPaint);
			}
		}
		return;
	}
	
	private native void extractLuminanceNative(byte[] yuv, int yuv_width,
			int yuv_height, int rgb_width, int rgb_height, int[] rgb);
	
	private native void applyGrayScale(byte[] yuv, int yuv_width,
			int yuv_height, int[] rgb);
	
	private native void toRGB565Native(byte[] yuv, int width,
			int height, int[] rgb);
	
	private native void processImage(byte[] data);
    
    static {
        System.loadLibrary("processImage");
    }

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO Auto-generated method stub
		
	}
	
}
