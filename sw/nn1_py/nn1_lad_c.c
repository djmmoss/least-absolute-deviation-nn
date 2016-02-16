#include "nn1_lad_c.h"

void MatrixVectorMul(double *A, double *b, double *ret, size_t rows, size_t cols);
void MatrixTransposeVectorMul(double *A, double *b, double *ret, size_t rows, size_t cols);
void VectorScalar(double *a, double b, double *ret, size_t m);
void VectorAdd(double *a, double *b, double *ret,  size_t m);
void VectorSub(double *a, double *b, double *ret,  size_t m);
void VectorUpdate(double *old, double *new, size_t m);
double f_err(double *a, double *b, size_t m);
void f_g(double *a, double *ret, size_t m);

void MatrixVectorMul(double *A, double *b, double *ret, size_t rows, size_t cols) {
    int i, j;
    for(i = 0; i < rows; i++){
        for(j = 0; j < cols; j++) {
            int index = i*cols + j;
            double inter = A[index]*b[j];
            ret[i] += inter;
        }
    }
}

void MatrixTransposeVectorMul(double *A, double *b, double *ret, size_t rows, size_t cols) {
    int i, j;
    for(i = 0; i < cols; i++){
        for(j = 0; j < rows; j++) {
            int index = i + j*cols;
            double inter = A[index]*b[j];
            ret[i] += inter;
        }
    }
}

void VectorScalar(double *a, double b, double *ret, size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        ret[i] = a[i]*b;
    }
}

void VectorAdd(double *a, double *b, double *ret,  size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        ret[i] = a[i] + b[i];
    }
}

void VectorSub(double *a, double *b, double *ret,  size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        ret[i] = a[i] - b[i];
    }
}

void VectorUpdate(double *old, double *new, size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        old[i] = new[i];
    }
}

void VectorZero(double *vec, size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        vec[i] = 0.0;
    }
}

double f_err(double *a, double *b, size_t m) {
    int i;
    double ret = 0.0;
    for(i = 0; i < m; i++){
        double sub = a[i] - b[i];
        ret += sub*sub;
    }
    return ret;
}

void f_g(double *a, double *ret, size_t m) {
    int i;
    for(i = 0; i < m; i++) {
        ret[i] = a[i];
        if(a[i] < -10.0) ret[i] = -10.0;
        if(a[i] > 10.0) ret[i] = 10.0;
    }
}


int nn1_lad_c(double *A, double *C, double *b, double *x, double *y, double *z, double lbda, double machine_eps, size_t m, size_t n, size_t r) {

    unsigned int i = 0;

    double x_new[n];
    VectorUpdate(x_new, x, n);

    double y_new[m];
    VectorUpdate(y_new, y, m);

    double z_new[r];
    VectorUpdate(z_new, z, r);

    double prev_x[n];
    VectorZero(prev_x, n);

    while(f_err(x_new, prev_x, n) > machine_eps) {
        i++;
        VectorUpdate(prev_x, x_new, n);

        // x_bar
        double Ay[n];
        VectorZero(Ay, n);
        MatrixTransposeVectorMul(A, y_new, Ay, m, n);

        double Cz[n];
        VectorZero(Cz, n);
        MatrixTransposeVectorMul(C, z_new, Cz, n, r);

        double AyCz[n];
        VectorAdd(Ay, Cz, AyCz, n);

        double x_stage[n];
        VectorSub(x_new, AyCz, x_stage, n);

        double x_bar[n];
        f_g(x_stage, x_bar, n);

        // y_bar
        double Ax_bar[m];
        VectorZero(Ax_bar, m);
        MatrixVectorMul(A, x_bar, Ax_bar, m, n);

        double Ax_barb[m];
        VectorSub(Ax_bar, b, Ax_barb, m);

        double y_stage[m];
        VectorAdd(y_new, Ax_barb, y_stage, m);

        double y_bar[m];
        VectorZero(y_bar, m);
        f_g(y_stage, y_bar, m);

        // z_bar
        double Cx_bar[r];
        VectorZero(Cx_bar, r);
        MatrixVectorMul(C, x_bar, Cx_bar, n, r);

        double z_stage[r];
        VectorSub(Cx_bar, z_new, z_stage, r);

        double z_bar[r];
        f_g(z_stage, z_bar, r);

        // diffs
        double x_barx[n];
        VectorSub(x_bar, x_new, x_barx, n);
        double x_diff[n];
        VectorScalar(x_barx, lbda, x_diff, n);

        double y_bary[m];
        VectorSub(y_bar, y_new, y_bary, m);
        double y_diff[m];
        VectorScalar(y_bary, 2.0*lbda, y_diff, m);

        double z_barz[r];
        VectorSub(Cx_bar, z_bar, z_barz, r);
        double z_diff[r];
        VectorScalar(z_barz, 2.0*lbda, z_diff, r);

        // update
        double new_x[n];
        VectorAdd(x_new, x_diff, new_x, n);
        VectorUpdate(x_new, new_x, n);

        double new_y[m];
        VectorAdd(y_new, y_diff, new_y, m);
        VectorUpdate(y_new, new_y, m);

        double new_z[r];
        VectorAdd(z_new, z_diff, new_z, r);
        VectorUpdate(z_new, new_z, r);
    }

    return i;
}
