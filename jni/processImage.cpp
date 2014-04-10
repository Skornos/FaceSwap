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

	//KalmanObject *Kalman = new KalmanObject();
	//KalmanObject *Kalman2 = new KalmanObject();

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
				//KF.transitionMatrix = *(Mat_<float>(4, 4) << 1,0,0,0,   0,1,0,0,  0,0,1,0,  0,0,0,1);
				Kalman->measurement.setTo(Scalar(0));

				// init...
				Kalman->KF.statePre.at<float>(0) = center_x;
				Kalman->KF.statePre.at<float>(1) = center_y;
				Kalman->KF.statePre.at<float>(2) = 0;
				Kalman->KF.statePre.at<float>(3) = 0;
				Kalman->KF.statePre.at<float>(4) = 0;
				Kalman->KF.statePre.at<float>(5) = 0;
				//KF.measurementMatrix = *(Mat_(2, 6) << 1,0,1,0,0.5,0, 0,1,0,1,0,0.5);
				setIdentity(Kalman->KF.measurementMatrix);
				setIdentity(Kalman->KF.processNoiseCov, Scalar::all(1e-2));
				setIdentity(Kalman->KF.measurementNoiseCov, Scalar::all(1e-1));
				setIdentity(Kalman->KF.errorCovPost, Scalar::all(.1));
			}

			//__android_log_print(ANDROID_LOG_INFO, "native", "Kalman!");
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

}

