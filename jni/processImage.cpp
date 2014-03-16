#include <string.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>

#include <ctype.h>

#include "opencv2/video/tracking.hpp"
#include "opencv2/highgui/highgui.hpp"

#include "processImage.h"

using namespace cv;
using namespace std;

extern "C" {

bool first_time = true;
KalmanFilter KF(6, 2, 0);
Mat_<float> measurement(2,1);

jintArray Java_fit_vutbr_faceswap_CameraPreview_kalmanFilter( JNIEnv* env,
                                                  jobject thiz,
                                                  jint center_x,
                                                  jint center_y) {
	// kalman filter initialization
	if (first_time) {
		KF.transitionMatrix = *(Mat_(6, 6) << 1,0,1,0,0.5,0, 0,1,0,1,0,0.5, 0,0,1,0,1,0, 0,0,0,1,0,1, 0,0,0,0,1,0, 0,0,0,0,0,1);
		measurement.setTo(Scalar(0));

		// init...
		KF.statePre.at<float>(0) = center_x;
		KF.statePre.at<float>(1) = center_y;
		KF.statePre.at<float>(2) = 0;
		KF.statePre.at<float>(3) = 0;
		KF.statePre.at<float>(4) = 0;
		KF.statePre.at<float>(5) = 0;
		//KF.measurementMatrix = *(Mat_(2, 6) << 1,0,1,0,0.5,0, 0,1,0,1,0,0.5);
		setIdentity(KF.measurementMatrix);
		setIdentity(KF.processNoiseCov, Scalar::all(1e-2));
		setIdentity(KF.measurementNoiseCov, Scalar::all(1e-1));
		setIdentity(KF.errorCovPost, Scalar::all(.1));
	}

	// First predict, to update the internal statePre variable
	Mat prediction = KF.predict();
	Point predictPt(prediction.at<float>(0),prediction.at<float>(1));

	measurement(0) = center_x;
	measurement(1) = center_y;

	Point measPt(measurement(0),measurement(1));

	// The "correct" phase that is going to use the predicted value and our measurement
	Mat estimated = KF.correct(measurement);
	Point statePt(estimated.at<float>(0),estimated.at<float>(1));

	//__android_log_print(ANDROID_LOG_INFO, "native", "x = %d, y = %d", statePt.x, statePt.y);

	jintArray ret = env->NewIntArray(2);
	jint *narr = env->GetIntArrayElements(ret, NULL);

	narr[0] = statePt.x;
	narr[1] = statePt.y;

	env->ReleaseIntArrayElements(ret, narr, NULL);

	first_time = false;

	return ret;
}

void Java_fit_vutbr_faceswap_CameraPreview_HeadPoseNative( JNIEnv* env,
                                                  jobject thiz,
                                                  jbyteArray jyuv,
                                                  jint yuv_width,
                                                  jint yuv_height,
                                                  jint rgb_width,
                                                  jint rgb_height,
                                                  jintArray jrgb)
{
	jbyte *yuv = env->GetByteArrayElements(jyuv, 0);
	env->GetArrayLength(jyuv);
	env->ReleaseByteArrayElements(jyuv, yuv, 0);
}

void Java_fit_vutbr_faceswap_CameraPreview_extractLuminanceNative( JNIEnv* env,
                                                  jobject thiz,
                                                  jbyteArray jyuv,
                                                  jint yuv_width,
                                                  jint yuv_height,
                                                  jint rgb_width,
                                                  jint rgb_height,
                                                  jintArray jrgb)
{

	jbyte *yuv = env->GetByteArrayElements(jyuv, 0);
	jint *rgb = env->GetIntArrayElements(jrgb, 0);

	/*
	if (rgb_width > yuv_width || rgb_height > yuv_height) {
		LOGE("Error in extractLuminance");
		return;
	}

	int w_scale = yuv_width / rgb_width;
	int h_scale = yuv_height / rgb_height;

	if (yuv_width != w_scale * rgb_width || yuv_height != h_scale * rgb_height) {
		LOGE("Error in extractLuminance");
		return;
	}
	*/

	int writePointer = 0;
	for (int row = 0; row < yuv_height; row ++) {
		int readPointer = row * yuv_width;
		for (int column = 0; column < yuv_width; column ++) {
			int Y = yuv[readPointer++];
			//readPointer ++;
			if (Y < 0)
				Y += 255;
			rgb[writePointer++] = Y * 0x00010101;
		}
	}

	env->ReleaseByteArrayElements(jyuv, yuv, 0);
	env->ReleaseIntArrayElements(jrgb, rgb, 0);

	return;
}

/**
 * Converts semi-planar YUV420 as generated for camera preview into RGB565
 * format for use as an OpenGL ES texture. It assumes that both the input
 * and output data are contiguous and start at zero.
 *
 * @param yuvs the array of YUV420 semi-planar data
 * @param rgbs an array into which the RGB565 data will be written
 * @param width the number of pixels horizontally
 * @param height the number of pixels vertically
 */

void Java_fit_vutbr_faceswap_CameraPreview_toRGB565(JNIEnv* env,
													jobject thiz,
													jbyteArray jyuv,
													jint width,
													jint height,
													jbyteArray jrgb)
{
	jbyte *yuv = env->GetByteArrayElements(jyuv, 0);
	jbyte *rgb = env->GetByteArrayElements(jrgb, 0);

	//the end of the luminance data
    int lumEnd = width * height;
    //points to the next luminance value pair
    int lumPtr = 0;
    //points to the next chromiance value pair
    int chrPtr = lumEnd;
    //points to the next byte output pair of RGB565 value
    int outPtr = 0;
    //the end of the current luminance scanline
    int lineEnd = width;

    while (true) {

        //skip back to the start of the chromiance values when necessary
        if (lumPtr == lineEnd) {
            if (lumPtr == lumEnd) break; //we've reached the end
            //division here is a bit expensive, but's only done once per scanline
            chrPtr = lumEnd + ((lumPtr  >> 1) / width) * width;
            lineEnd += width;
        }

        //read the luminance and chromiance values
        int Y1 = yuv[lumPtr++] & 0xff;
        int Y2 = yuv[lumPtr++] & 0xff;
        int Cr = (yuv[chrPtr++] & 0xff) - 128;
        int Cb = (yuv[chrPtr++] & 0xff) - 128;
        int R, G, B;

        //generate first RGB components
        B = Y1 + ((454 * Cb) >> 8);
        if(B < 0) B = 0; else if(B > 255) B = 255;
        G = Y1 - ((88 * Cb + 183 * Cr) >> 8);
        if(G < 0) G = 0; else if(G > 255) G = 255;
        R = Y1 + ((359 * Cr) >> 8);
        if(R < 0) R = 0; else if(R > 255) R = 255;
        //NOTE: this assume little-endian encoding
        rgb[outPtr++]  = (jbyte) (((G & 0x3c) << 3) | (B >> 3));
        rgb[outPtr++]  = (jbyte) ((R & 0xf8) | (G >> 5));

        //generate second RGB components
        B = Y2 + ((454 * Cb) >> 8);
        if(B < 0) B = 0; else if(B > 255) B = 255;
        G = Y2 - ((88 * Cb + 183 * Cr) >> 8);
        if(G < 0) G = 0; else if(G > 255) G = 255;
        R = Y2 + ((359 * Cr) >> 8);
        if(R < 0) R = 0; else if(R > 255) R = 255;
        //NOTE: this assume little-endian encoding
        rgb[outPtr++]  = (jbyte) (((G & 0x3c) << 3) | (B >> 3));
        rgb[outPtr++]  = (jbyte) ((R & 0xf8) | (G >> 5));
    }
}

/*
void Java_fit_vutbr_faceswap_CameraPreview_YUVtoRBG( JNIEnv* env,
                                                  jobject thiz,
                                                  jintArray jrgb,
                                                  jbyteArray jyuv,
                                                  jint width,
                                                  jint height)
{
    int             sz;
    int             i;
    int             j;
    int             Y;
    int             Cr = 0;
    int             Cb = 0;
    int             pixPtr = 0;
    int             jDiv2 = 0;
    int             R = 0;
    int             G = 0;
    int             B = 0;
    int             cOff;
	int w = width;
	int h = height;
	sz = w * h;

	jbyte* yuv = env->GetByteArrayElements(jyuv, 0);
	jbyte* rgb = env->GetIntArrayElements(jrgb, 0);

	for(j = 0; j < h; j++) {
			   pixPtr = j * w;
			   jDiv2 = j >> 1;
			   for(i = 0; i < w; i++) {
					   Y = yuv[pixPtr];
					 if(Y < 0) Y += 255;
					   if((i & 0x1) != 1) {
							   cOff = sz + jDiv2 * w + (i >> 1) * 2;
							   Cb = yuv[cOff];
							   if(Cb < 0) Cb += 127; else Cb -= 128;
							   Cr = yuv[cOff + 1];
							   if(Cr < 0) Cr += 127; else Cr -= 128;
					   }
					   R = Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);
					   if(R < 0) R = 0; else if(R > 255) R = 255;
					   G = Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1) + (Cr >> 3) + (Cr >> 4) + (Cr >> 5);
					   if(G < 0) G = 0; else if(G > 255) G = 255;
					   B = Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);
					   if(B < 0) B = 0; else if(B > 255) B = 255;
					   rgbData[pixPtr++] = 0xff000000 + (B << 16) + (G << 8) + R;
			   }
	}
	env->ReleaseByteArrayElements(jyuv, yuv, 0);
	env->ReleaseIntArrayElements(jrgb, rgb, 0);
}*/

}
