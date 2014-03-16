/**
* Copyright (C) 2013 Imran Akthar (www.imranakthar.com)
* imran@imranakthar.com
*/

package fit.vutbr.faceswap;

import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.Video;

import android.util.Log;


public class HeadPose {
	enum HeadPoseStatus {NONE, KEYFRAME, TRACKING};
	public MatOfPoint features_prev;
	public MatOfPoint features_next,tempcorners;
	public int cornerCount,maxCorners,focalLength;
	public MatOfPoint3f modelPoints;
	public Mat Rvec;
	public Mat Tvec;
	public CascadeClassifier haarCascade;
	public Mat TrackingFrame,nextFrame,csImage,hsv,hue,mask;
	public List<Mat> hsvarray,huearray;
	public float PI=3.141592f;
	HeadPoseOptions hpo;
	HeadPoseStatus hpstatus;
	Rect[] TempFace;
	boolean backprojMode;
	boolean selectObject;
	int trackObject;
	boolean showHist;
	Point origin;
	Rect selection;
	int vmin,vmax,smin;
	Rect trackWindow;
	
	public MatOfPoint2f featurestracked;
	
	//http://stackoverflow.com/questions/9701276/opencv-tracking-using-optical-flow
	//http://stackoverflow.com/questions/10159236/feature-tracking-using-optical-flow/10172247#10172247


	public HeadPose() {
		hpo=new HeadPoseOptions();
		hpstatus=HeadPoseStatus.NONE;
		cornerCount=0;
		maxCorners=100;
		features_prev= new MatOfPoint();
		features_next= new MatOfPoint();
		tempcorners=new MatOfPoint();
		Rvec=new Mat(3,1,CvType.CV_64FC1);
		Tvec=new Mat(3,1,CvType.CV_64FC1);
		modelPoints = new MatOfPoint3f();
		focalLength=hpo.focalLength;
	}

	public void hpFind(Mat mRgba,Mat mGray,HeadPose hp,Rect[] facesArray) {
		
		int i;
		Log.i("HeadPose","hpFind:Total Faces Found: "+facesArray.length);
		Log.i("HeadPose", hp.hpstatus+"");
		
		// Tracking is currently OFF
		if(hp.hpstatus==HeadPoseStatus.NONE)
		{
			if(facesArray.length<1)
				return;
			/* for (i = 0; i < facesArray.length; i++)
		            Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), new Scalar(0,255,0), 3);*/
			 //imran displaying face rectabgle only when its not tracking
			
			TempFace=facesArray.clone();
			hp.cornerCount=hp.maxCorners;
			Rect roi = new Rect((int)facesArray[0].tl().x,(int)(facesArray[0].tl().y),facesArray[0].width,(int)(facesArray[0].height));//imran
			Mat cropped = new Mat();
			Mat GrayClone=new Mat();
			GrayClone=mGray.clone();
			cropped = GrayClone.submat(roi);
			hpFindCorners(cropped,hp);
			//features detected in next frame...we need to use these and track all the new frames	
			if(hp.features_next.total()<4)
				return;
			else {
				hp.TrackingFrame=new Mat(mRgba.size(),CvType.CV_8UC4);
				hp.TrackingFrame=mRgba.clone();			
				hp.hpstatus=HeadPoseStatus.TRACKING;
			}
				
			//returning if features are less than 4.does not make a rectangle		 
		}
		
		// Tracking head
		if(hp.hpstatus==HeadPoseStatus.TRACKING) {
			hpTrack(mRgba,hp,facesArray);
		}
		
	}

	void hpTrack(Mat mRgba,HeadPose hp,Rect[] facesArray) {
		MatOfByte status = new MatOfByte();
		Mat prev=new Mat(mRgba.size(),CvType.CV_8UC1);
		Mat curr=new Mat(mRgba.size(),CvType.CV_8UC1);
		featurestracked =new MatOfPoint2f();
		MatOfFloat err=new MatOfFloat();
		int i,j,count;
		TermCriteria optical_flow_termination_criteria=new TermCriteria();//=(TermCriteria.MAX_ITER|TermCriteria.EPS,20,.3);//  ( CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, .3 );
		optical_flow_termination_criteria.epsilon=.3;
		optical_flow_termination_criteria.maxCount =20;
		
		Imgproc.cvtColor(hp.TrackingFrame, prev, Imgproc.COLOR_RGBA2GRAY,0);
		Imgproc.cvtColor(mRgba, curr, Imgproc.COLOR_RGBA2GRAY,0);
		//http://stackoverflow.com/questions/11273588/how-to-convert-matofpoint-to-matofpoint2f-in-opencv-java-api
		//imran converging hp.features_next to MatofPoint2f
		MatOfPoint2f features_next2f=new MatOfPoint2f(hp.features_next.toArray());
		Video.calcOpticalFlowPyrLK(prev, curr, features_next2f, featurestracked, status, err);
		
		count = 0;
		for (i = 0; i < status.total(); i += 1) {
			if (status.toList().get(i) == 1) {
		      count += 1;
		    }
		}
		
		//http://stackoverflow.com/questions/5943181/android-java-percentage-calculation
		Double accuracy=((double)count/status.total())*100;
		Log.i("Accuracy","Total Status "+status.total());
		Log.i("Accuracy","Total Count "+count);
		Log.i("Accuracy","Accuracy of Optical is "+accuracy);
		if(accuracy<80.0f)//imran ...important stuff!
			hp.hpstatus=HeadPoseStatus.NONE;//imran ressetting to track face as points of way beyond
		//imran doing this will reset the face tracker
		
		//Video.CamShift(probImage, window, criteria);
		Point center = new Point();
		for( i = 0; i < featurestracked.total(); i++ )
		{ 
			//center.x=facearray1[0].x + hp.corners.toList().get(i).x;
			//center.y=facearray1[0].y + hp.corners.toList().get(i).y;
			center.x=hp.TempFace[0].x+featurestracked.toList().get(i).x;
			center.y=hp.TempFace[0].y+featurestracked.toList().get(i).y;
			Core.circle( mRgba, center, 6, new Scalar(0,255,0), -1, 8, 0 );
		}
	
	}

	public void hpFindCorners(Mat cropped,HeadPose hp) {
		/// Parameters for Shi-Tomasi algorithm
		double qualityLevel = 0.01;
		double minDistance = 10;
		int blockSize = 3;
		boolean useHarrisDetector = false;
		double k = 0.04; 
		Imgproc.goodFeaturesToTrack(cropped, hp.features_next,hp.cornerCount, qualityLevel, minDistance, new Mat(), blockSize, useHarrisDetector, k);
		//Imgproc.goodFeaturesToTrack(cropped, hp.features_next, hp.cornerCount, qualityLevel, minDistance);
		
		/* FAST corner detection
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		FeatureDetector fast = FeatureDetector.create(FeatureDetector.FAST);
		fast.detect(cropped, keypoints);
		
		KeyPoint[] keypointsArr = keypoints.toArray();
		Point[] pointArr = new Point[keypointsArr.length];
		
		for (int i = 0; i < keypointsArr.length; i++) {
			KeyPoint keypoint = keypointsArr[i];
			pointArr[i] = keypoint.pt;
		}
		
		hp.features_next.fromArray(pointArr);
		*/
		Log.i("HeadPose","FindCorner:Total Corners Found"+hp.features_next.total());
		
	}

	public Point3 hpmodel(double x,double y) {
		return new Point3(x - 0.5, -y + 0.5, 0.5 * Math.sin(PI *x));
	}
}
