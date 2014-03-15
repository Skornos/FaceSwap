package fit.vutbr.faceswap;

import java.util.Arrays;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import android.util.Log;

public class Camshift {
		
	private Mat 						roi_hist;
	private 							RotatedRect mCamshiftRect;
		
	public CamshiftTrackedObject 		obj;
	int 								hist_bins=30;           //number of histogram bins
    int 								hist_range[]= {0,180};//histogram range
    int 								range;
    Mat 								bgr;
	
	public Camshift() {
		obj = new CamshiftTrackedObject();
	}
	
	public void create_tracked_object(Rect[] facesArray, Mat mRgba)
	{		
		obj.hsv=new Mat(mRgba.size(),CvType.CV_8UC3);
		  // obj->hsv  = cvCreateImage(cvGetSize(image), 8, 3);
		obj.mask=new Mat(mRgba.size(),CvType.CV_8UC1);
		  //obj->mask = cvCreateImage(cvGetSize(image), 8, 1);
		obj.hue=new Mat(mRgba.size(),CvType.CV_8UC1);
		  //obj->hue  = cvCreateImage(cvGetSize(image), 8, 1);
		obj.prob=new Mat(mRgba.size(),CvType.CV_8UC1);
		  //obj->prob = cvCreateImage(cvGetSize(image), 8, 1);
		update_hue_image(facesArray, mRgba);
		
		
		float max_val = 0.f;
		
		//create a histogram representation for the face
		//Rect roi = new Rect((int)region[0].tl().x,(int)(region[0].tl().y),region[0].width,region[0].height);//imran 
		Mat tempmask=new Mat(obj.mask.size(),CvType.CV_8UC1);			 
		  //tempmask=cs.obj.mask.submat(roi);
		tempmask=obj.mask.submat(facesArray[0]);
		  
		  
		 // Log.i("CamShifting","Mask Size"+tempmask.size());
		  //cant use mask here as method wil not take
		MatOfFloat ranges = new MatOfFloat(0f, 256f);
		MatOfInt histSize = new MatOfInt(25);
		  //List<Mat> histList = Arrays.asList( new Mat[] {new Mat(), new Mat(), new Mat()} );
		 // Imgproc.calcHist(cs.obj.huearray, new MatOfInt(0),cs.obj.mask, cs.obj.hist, histSize, ranges);
		 // List<Mat> images = Arrays.asList(cs.obj.hsv.submat(roi));
		List<Mat> images = Arrays.asList(obj.huearray.get(0).submat(facesArray[0]));
		Imgproc.calcHist(images, new MatOfInt(0),tempmask, obj.hist, histSize, ranges);
		  
		Core.normalize(obj.hist, obj.hist);
		  //Core.normalize(cs.obj.hist, cs.obj.hist, 0,255,Core.NORM_MINMAX);
		obj.prev_rect=facesArray[0];
		Log.i("Normalized Histogram","Normalized Histogram Starting "+obj.hist);
		
	}
	
	public void update_hue_image(Rect[] facesArray, Mat mRgba)
	{
		int vmin = 65, vmax = 256, smin = 55;
		bgr=new Mat(mRgba.size(),CvType.CV_8UC3);		

		Imgproc.cvtColor(mRgba, bgr, Imgproc.COLOR_RGBA2BGR);
		  //imran converting RGBA to BGR 
		//convert to HSV color model
		Imgproc.cvtColor(bgr,obj.hsv,Imgproc.COLOR_BGR2HSV);
		  
		//mask out-of-range values
		Core.inRange(obj.hsv, new Scalar(0, smin,Math.min(vmin,vmax)),new Scalar(180, 256,Math.max(vmin, vmax)), obj.mask);
		  
		obj.hsvarray.clear();
		obj.huearray.clear();
		obj.hsvarray.add(obj.hsv);
		obj.huearray.add(obj.hue);
		MatOfInt from_to = new MatOfInt(0,0);
		//extract the hue channel, split: src, dest channels
		Core.mixChannels(obj.hsvarray, obj.huearray,from_to);
		
	}
	
	RotatedRect camshift_track_face(Rect[] facesArray, Mat mRgba)
	{
		
		MatOfFloat ranges = new MatOfFloat(0f, 256f);
		//ConnectedComp components;
		update_hue_image(facesArray, mRgba);
		Imgproc.calcBackProject(obj.huearray, new MatOfInt(0),obj.hist,obj.prob, ranges,255);
		Core.bitwise_and(obj.prob,obj.mask,obj.prob,new Mat());
		
		obj.curr_box = Video.CamShift(obj.prob, obj.prev_rect, new TermCriteria(TermCriteria.EPS,10,1));	
		//Log.i("Tracked Rectangle","Tracked Rectangle"+obj.prev_rect);
		//Log.i("Tracked Rectangle","New Rectangle"+obj.curr_box.boundingRect());
		obj.prev_rect = obj.curr_box.boundingRect();
		obj.curr_box.angle =- obj.curr_box.angle;
		return obj.curr_box;
		
		//obj.incTrackedFrames();
	}
	
	// muj puvodni camshift
	public void track(Rect[] facesArray, Mat yuv) {
    	Mat rgba;
		Mat mask;
		Rect track_window;
		
		Mat hsv_roi = new Mat();
		Mat dst = new Mat();
        MatOfInt channels = new MatOfInt(0);
		MatOfInt histSize = new MatOfInt(180);
		MatOfFloat ranges = new MatOfFloat(0.0f, 180.0f);
		
    	// detekovan alespon 1 oblicej a je zapnuty camshift	
	    if (obj.getTrackedFrames()== 0) {	
	    	mask = new Mat();
	    	rgba = new Mat();
	    	
	    	// YUV420 na RGB
			Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV420p2RGB, 4);
			
			// RGB na HSV
			Imgproc.cvtColor(rgba, hsv_roi, Imgproc.COLOR_RGB2HSV_FULL);
			rgba.release();
			
	    	// camshift zacina od startu
			// tvorba a normalizace histogramu
	    	if (obj.getTrackedFrames() == 0) {
				// maska
				Core.inRange(hsv_roi, new Scalar(0.0f,60.0f,32.0f), new Scalar(180.0f,255.0f,255.0f), mask);
							
				// histogram				
				roi_hist = new Mat();
		        Imgproc.calcHist(Arrays.asList(hsv_roi), channels, mask, roi_hist, histSize, ranges);
		        mask.release();
		        
		        // normalizace histogramu
		        Core.normalize(roi_hist, roi_hist, 0, 255, Core.NORM_MINMAX);
		        
		        track_window = facesArray[0].clone();
	    	}		
	    	// camshift prave probiha
	    	else {
	    		track_window = mCamshiftRect.boundingRect();
	    	}
	        	        
	        // backprojection
	        Imgproc.calcBackProject(Arrays.asList(hsv_roi), channels, roi_hist, dst, ranges, 1);
	        
	        
	        // CAMSHIFT
	        mCamshiftRect = Video.CamShift(dst, track_window, new TermCriteria(TermCriteria.EPS | TermCriteria.COUNT,10,1));
	        obj.incTrackedFrames();
		}
	    
	    if (obj.getTrackedFrames() == obj.MAX_CAMSHIFT_FRAMES) {
	    	obj.setTrackedFrames(0);
		    roi_hist.release();
	    }
	    
	    hsv_roi.release();
	    histSize.release();
	    dst.release();
	    channels.release();
	    ranges.release();
	    
    }
	
	public RotatedRect getRotatedRect() {
		return mCamshiftRect;
	}
	
}
