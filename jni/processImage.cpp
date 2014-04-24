#include <string.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <list>

#include <ctype.h>

#include "opencv2/video/tracking.hpp"
#include "opencv2/highgui/highgui.hpp"

#include "processImage.h"
#include "poissonBlending.hpp"

using namespace cv;
using namespace std;



extern "C" {

	class KalmanObject {
		public:
			bool first_time;
			KalmanFilter KF;
			Mat_<float> measurement;

			KalmanObject();
			void setFirstTime();
	};

	KalmanObject::KalmanObject(): first_time(true), KF(6, 2, 0), measurement(2,1) {}

	void KalmanObject::setFirstTime() {
		first_time = false;
	}

	std::list<KalmanObject> kalmanFilterList;
	std::vector<KalmanObject> kalmanFilterVector;
	std::list<KalmanObject>::iterator it;

	// Adapted from: http://www.morethantechnical.com/2011/06/17/simple-kalman-filter-for-tracking-using-opencv-2-2-w-code/
	JNIEXPORT jintArray JNICALL	 Java_fit_vutbr_faceswap_CameraPreview_kalmanFilterNative( JNIEnv* env,
													  jobject thiz,
													  jintArray _centers) {

		jint *centers = env->GetIntArrayElements(_centers, 0);
		jint centers_length = env->GetArrayLength(_centers);

		jintArray ret = env->NewIntArray(centers_length);
		jint *narr = env->GetIntArrayElements(ret, NULL);

		int faces_length = centers_length/2;
		int delta = faces_length - kalmanFilterVector.size();

		// pocet prichozich obliceju je vetsi nez velikost vektoru
		// => pridam misto do vektoru
		if (delta > 0) {
			for (int i=0; i < delta; i++) {
				KalmanObject k;
				kalmanFilterVector.push_back(k);
			}
		}
		// vektor je vetsi nez pocet prichozich obliceju
		// => smazu misto ve vektoru
		else if (delta < 0) {
			for (int i=delta; i < 0; i++) {
				kalmanFilterVector.pop_back();
			}
		}

		//LOGD("kalmanFilterVector size: %d", kalmanFilterVector.size());

		int i = 0;
		for (vector<KalmanObject>::iterator Kalman = kalmanFilterVector.begin(); Kalman != kalmanFilterVector.end(); ++Kalman) {
			int center_x = centers[i];
			int center_y = centers[i+1];

			// kalman filter initialization
			if (Kalman->first_time) {
				Kalman->KF.transitionMatrix = *(Mat_<float>(6, 6) << 1,0,1,0,0.5,0, 0,1,0,1,0,0.5, 0,0,1,0,1,0, 0,0,0,1,0,1, 0,0,0,0,1,0, 0,0,0,0,0,1);
				//Kalman->KF.measurementMatrix = *(Mat_<float>(2, 6) << 1,0,1,0,0.5,0, 0,1,0,1,0,0.5);
				//Kalman->KF.transitionMatrix = *(Mat_<float>(4, 4) << 1,0,1,0,   0,1,0,1,  0,0,1,0,  0,0,0,1);
				Kalman->measurement.setTo(Scalar(0));

				// init...
				Kalman->KF.statePre.at<float>(0) = center_x;
				Kalman->KF.statePre.at<float>(1) = center_y;
				Kalman->KF.statePre.at<float>(2) = 0;
				Kalman->KF.statePre.at<float>(3) = 0;
				Kalman->KF.statePre.at<float>(4) = 0;
				Kalman->KF.statePre.at<float>(5) = 0;

				setIdentity(Kalman->KF.measurementMatrix);
				setIdentity(Kalman->KF.processNoiseCov, Scalar::all(1e-2));
				setIdentity(Kalman->KF.measurementNoiseCov, Scalar::all(1e-1));
				setIdentity(Kalman->KF.errorCovPost, Scalar::all(.1));
			}

			{
				// First predict, to update the internal statePre variable
				Mat prediction = Kalman->KF.predict();
				Point predictPt(prediction.at<float>(0),prediction.at<float>(1));

				Kalman->measurement(0) = center_x;
				Kalman->measurement(1) = center_y;

				Point measPt(Kalman->measurement(0), Kalman->measurement(1));

				// The "correct" phase that is going to use the predicted value and our measurement
				Mat estimated = Kalman->KF.correct(Kalman->measurement);

				Point statePt(estimated.at<float>(0),estimated.at<float>(1));

				narr[i] = statePt.x;
				narr[i+1] = statePt.y;

				Kalman->first_time = false;
				i += 2;
			}
		}

		env->ReleaseIntArrayElements(_centers, centers, 0);
		env->ReleaseIntArrayElements(ret, narr, 0);

		return ret;
	}

	// http://www.41post.com/3470/programming/android-retrieving-the-camera-preview-as-a-pixel-array
	// http://stackoverflow.com/questions/12469730/confusion-on-yuv-nv21-conversion-to-rgb
	void Java_fit_vutbr_faceswap_CameraPreview_decodeYUV420SPNative(JNIEnv* env,
																jobject thiz,
																jintArray _rgb,
																jbyteArray _yuv420sp,
																jint width,
																jint height) {

		jbyte *yuv420sp = env->GetByteArrayElements(_yuv420sp, 0);
		jint *rgb = env->GetIntArrayElements(_rgb, 0);

		int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
			    }

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
			    if (g < 0)
			    	g = 0;
			   	else if (g > 262143)
					g = 262143;

				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	        env->ReleaseByteArrayElements(_yuv420sp, yuv420sp, 0);
	        env->ReleaseIntArrayElements(_rgb, rgb, 0);
	}

	// http://docs.opencv.org/doc/tutorials/imgproc/histograms/histogram_comparison/histogram_comparison.html
	jdouble Java_fit_vutbr_faceswap_CameraPreview_compareHistNative(JNIEnv* env,
			jobject thiz,
			jlong face0Addr,
			jlong face1Addr) {

		Mat & face0=*(Mat*)face0Addr;
		Mat & face1=*(Mat*)face1Addr;

	    /// Using 30 bins for hue and 32 for saturation
		int h_bins = 50; int s_bins = 60;
		int histSize[] = { h_bins, s_bins };

		// hue varies from 0 to 256, saturation from 0 to 180
		float h_ranges[] = { 0, 256 };
		float s_ranges[] = { 0, 180 };

		const float* ranges[] = { h_ranges, s_ranges };

		// Use the o-th and 1-st channels
		int channels[] = { 0, 1 };

		/// Histograms
		MatND hist0;
		MatND hist1;

		/// Calculate the histograms for the HSV images
		calcHist( &face0, 1, channels, Mat(), hist0, 2, histSize, ranges, true, false );
		normalize( hist0, hist0, 0, 1, NORM_MINMAX, -1, Mat() );

		calcHist( &face1, 1, channels, Mat(), hist1, 2, histSize, ranges, true, false );
		normalize( hist1, hist1, 0, 1, NORM_MINMAX, -1, Mat() );

		double compar_c = compareHist( hist0, hist1, CV_COMP_CORREL );

		return compar_c;
	}

/*
	Mat Java_fit_vutbr_faceswap_CameraPreview_pbmethod(Mat img1, Mat img2, Rect ROI, int posX, int posY){

		int width1, width2, height1, height2;
		width1 = img1.cols;
		width2 = img2.cols;
		height1 = img1.rows;
		height2 = img2.rows;

		Mat roiImg;
		roiImg = img1(ROI);

		IplImage* myresult;
		IplImage abc = IplImage(img2);
		IplImage subimg = IplImage(roiImg);

		myresult = poisson_blend(&abc,&subimg,posX,posY);
		Mat result(myresult);
		return result;
	}
*/

}

