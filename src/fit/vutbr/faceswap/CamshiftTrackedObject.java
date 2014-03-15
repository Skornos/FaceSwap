package fit.vutbr.faceswap;

import java.util.List;
import java.util.Vector;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;

public class CamshiftTrackedObject {
	
	public static final int				MAX_CAMSHIFT_FRAMES = 20;
	
	
	Mat 								hsv,hue,mask,prob;
	Rect 								prev_rect;
	RotatedRect  						curr_box;
	Mat 								hist;
	public List<Mat> 					hsvarray,huearray;
	
	private int 						trackedFrames;

	
	public CamshiftTrackedObject() {
		//hsv=new Mat();
		//hue=new Mat();
		//mask=new Mat();
		//prob=new Mat();
		hist=new Mat();
		prev_rect=new Rect();
		curr_box=new RotatedRect();
		hsvarray=new Vector<Mat>();
		huearray=new Vector<Mat>();
		trackedFrames = 0;
		
	}
	
	public int getTrackedFrames() {
		return trackedFrames;
	}
	
	public void setTrackedFrames(int frames) {
		trackedFrames = frames;
	}
	
	public void incTrackedFrames() {
		trackedFrames++;
	}
}
