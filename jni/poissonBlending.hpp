#include <iostream>
#include <complex>
#include "opencv/cv.h"
#include "opencv/cxcore.h"
#include "opencv/highgui.h"
#include "math.h"

using namespace std;
using namespace cv;

IplImage *poisson_blend(IplImage *I, IplImage *mask, int posx, int posy);
void poisson_solver(const IplImage *img, IplImage *gxx , IplImage *gyy, Mat &result);
void transpose(double *mat, double *mat_t,int h,int w);
void idst(double *gtest, double *gfinal,int h,int w);
void dst(double *gtest, double *gfinal,int h,int w);
void lapy( const IplImage *img, IplImage *gyy);
void lapx( const IplImage *img, IplImage *gxx);
void getGradienty( const IplImage *img, IplImage *gy);
void getGradientx( const IplImage *img, IplImage *gx);
