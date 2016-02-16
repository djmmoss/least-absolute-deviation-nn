#include <stdio.h>
#include <stdlib.h>
#include <float.h>
#include <time.h>

#include "mkl.h"

struct nn1_results {
    int loops;
    double time;
};

void f_g2(double *in, double rho, double qi, MKL_INT m);
void update_xy(double *in, double *bar, double lbda, MKL_INT m);
void update_z(double *C, double *x_bar, double *z_bar, double lbda, double *z, MKL_INT n, MKL_INT r);
double calc_err(double *a, double *b, size_t m);
double calc_nrm2(double *x, double *prev_x, MKL_INT m);
void calc_x_bar(double *A, double *C, double *x, double *y, double *z, double *x_bar, MKL_INT m, MKL_INT n, MKL_INT r);
void calc_y_bar(double *A, double *b, double *x_bar, double *y, double *y_bar, MKL_INT m, MKL_INT n, MKL_INT r);
void calc_z_bar(double *C, double *x_bar, double *z_bar, MKL_INT m, MKL_INT n, MKL_INT r);

//A[0] = -1.0; A[1] = 1.0; A[2] = 0.0;
//A[3] = -0.5; A[4] = 1.0; A[5] = -0.5;
//A[6] = 0.5; A[7] = -1.0; A[8] = 1.0;
//A[9] = 1.0; A[10] = -1.0; A[11] = 1.0;
//A[12] = 1.0; A[13] = -0.5; A[14] = 0.5;
//A[15] = 2.0; A[16] = -1.0; A[17] = 1.0;
//A[18] = 1.0; A[19] = -1.0; A[20] = 1.0;
//A[21] = 0.5; A[22] = -1.0; A[23] = 1.0;
//A[24] = 0.5; A[25] = -0.75; A[26] = 1.0;
//A[27] = 2.0; A[28] = -2.0; A[29] = 3.0;
//A[30] = 0.0; A[31] = -1.0; A[32] = 1.0;
//A[33] = 1.0; A[34] = -1.0; A[35] = 3.0;

//b[0] = -1.0;
//b[1] = -0.5;
//b[2] = 1.0;
//b[3] = 1.0;
//b[4] = 0.5;
//b[5] = 1.0;
//b[6] = 0.0;
//b[7] = -0.5;
//b[8] = -0.25;
//b[9] = -3.0;
//b[10] = 0.0;
//b[11] = -2.0;
