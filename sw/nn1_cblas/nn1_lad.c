#include "nn1_lad.h"
#include "nn1_nav.h"

double r2() { return (double)rand() / (double)RAND_MAX; }

void f_g2(double *in, double rho, double qi, MKL_INT m) {
    MKL_INT i;
    for (i = 0; i < m; i++) {
        if (in[i] > rho) { in[i] = rho; }
        if (in[i] < qi)  { in[i] = qi; }
    }
}

void update_xy(double *in, double *bar, double lbda, MKL_INT m) {
    MKL_INT i;
    // -lbda*bar
    cblas_dscal(m, -lbda, bar, 1);

    // -diff = lbda*in + -lbda*bar
    cblas_daxpy(m, lbda, in, 1, bar, 1);

    // in = in + (-1)*-diff
    cblas_daxpy(m, -1.0, bar, 1, in, 1);
}

void update_z(double *C, double *x_bar, double *z_bar, double lbda, double *z, MKL_INT n, MKL_INT r) {
    // z_diff = (np.dot(C, x_bar) - z_bar)
    cblas_dgemv(CblasRowMajor, CblasNoTrans, n, r, 1.0, C, r, x_bar, 1, -1.0, z_bar, 1);

    // z = z + 2*lbda*z_diff
    cblas_daxpy(r, 2.0*lbda, z_bar, 1, z, 1);
}

double calc_err(double *a, double *b, size_t m) {
    int i;
    double ret = 0.0;
    for(i = 0; i < m; i++){
        double sub = a[i] - b[i];
        ret += sub*sub;
    }
    return ret;
}

double calc_nrm2(double *x, double *prev_x, MKL_INT m) {
    // prev_x = prev_x - x
    cblas_daxpy(m, -1.0, x, 1, prev_x, 1);

    return cblas_dnrm2(m, prev_x, 1);
}

void calc_x_bar(double *A, double *C, double *x, double *y, double *z, double *x_bar, MKL_INT m, MKL_INT n, MKL_INT r) {
    // x_stage = g(x - np.dot(np.transpose(A), y) + np.dot(np.transpose(C), z), 10, -10)
    //np.dot(np.transpose(C), z)
    cblas_dgemv(CblasRowMajor, CblasTrans, r, n, 1.0, C, n, z, 1, 0.0, x_bar, 1);

    // np.dot(np.transpose(A), y) + np.dot(np.transpose(C), z)
    cblas_dgemv(CblasRowMajor, CblasTrans, m, n, -1.0, A, n, y, 1, -1.0, x_bar, 1);

    // x - np.dot(np.transpose(A), y) + np.dot(np.transpose(C), z)
    cblas_daxpy(n, 1.0, x, 1, x_bar, 1);

    // x_bar.map(f_g)
    f_g2(x_bar, 2.0, -2.0, n);
}

void calc_y_bar(double *A, double *b, double *x_bar, double *y, double *y_bar, MKL_INT m, MKL_INT n, MKL_INT r) {
    // y_stage = y + np.dot(A, x_bar) - b
    // np.dot(A, x_bar) - b
    cblas_dgemv(CblasRowMajor, CblasNoTrans, m, n, 1.0, A, n, x_bar, 1, -1.0, y_bar, 1);

    // y + np.dot(A, x_bar) - b
    cblas_daxpy(m, 1.0, y, 1, y_bar, 1);

    // y_bar.map(f_g)
    f_g2(y_bar, 2.0, -2.0, m);
}

void calc_z_bar(double *C, double *x_bar, double *z_bar, MKL_INT m, MKL_INT n, MKL_INT r) {
    //z_stage = np.dot(C, x_bar) - z
    cblas_dgemv(CblasRowMajor, CblasNoTrans, n, r, 1.0, C, r, x_bar, 1, -1.0, z_bar, 1);

    // y_bar.map(f_g)
    f_g2(z_bar, 2.0, -2.0, r);
}

struct nn1_results run_nav(double *A, double *C, double *b, double *x, double *y, double *z, double lbda, double machine_eps, MKL_INT m, MKL_INT n, MKL_INT r) {
    struct nn1_results nav_res;
    struct timeval requestNavStart, requestNavEnd;
    gettimeofday(&requestNavStart, 0);
    int nav_i = nn1_lad_c(A, C, b, x, y, z, lbda, machine_eps, m, n, r);
    gettimeofday(&requestNavEnd, 0);

    nav_res.loops = nav_i;
    nav_res.time = (requestNavEnd.tv_sec - requestNavStart.tv_sec)*1E6 + (requestNavEnd.tv_usec - requestNavStart.tv_usec);

    return nav_res;
}

struct nn1_results run_mkl(double *A, double *C, double *b, double *x, double *y, double *z, double lbda, double machine_eps, MKL_INT m, MKL_INT n, MKL_INT r) {
    struct nn1_results mkl_res;
    MKL_INT i;
    MKL_INT count = 0;
    // Previous X
    double *prev_x;
    MKL_INT len_prev_x = n;
    prev_x = (double *)calloc(len_prev_x, sizeof(double));
    if( prev_x == NULL ){
        printf("prev_x Memory Fail\n");
        /*return 1;*/
    }

    for (i = 0; i < n; i++) { prev_x[i] = 0.0; }

    double *x_bar;
    double *y_bar;
    double *z_bar;
    MKL_INT len_x_bar = n;
    MKL_INT len_y_bar = m;
    MKL_INT len_z_bar = r;
    x_bar = (double *)calloc(len_x_bar, sizeof(double));
    y_bar = (double *)calloc(len_y_bar, sizeof(double));
    z_bar = (double *)calloc(len_z_bar, sizeof(double));
    if( x_bar == NULL || y_bar == NULL || z_bar == NULL ){
        printf("bar Memory Fail\n");
        /*return 1;*/
    }

    struct timeval requestStart, requestEnd;
    gettimeofday(&requestStart, 0);
    /*while (calc_nrm2(x, prev_x, n) > machine_eps) {*/
    while (calc_err(x, prev_x, n) > machine_eps) {
        count++;
        for (i = 0; i < n; i++) { prev_x[i] = x[i]; }

        // Calculate x_bar
        for (i = 0; i < n; i++) { x_bar[i] = 0.0; }
        calc_x_bar(A, C, x, y, z, x_bar, m, n, r);

        // Calculate y_bar
        for (i = 0; i < m; i++) { y_bar[i] = b[i]; }
        calc_y_bar(A, b, x_bar, y, y_bar, m, n, r);

        // Calculate z_bar
        for (i = 0; i < r; i++) { z_bar[i] = z[i]; }
        calc_z_bar(C, x_bar, z_bar, m, n, r);

        // Update x
        update_z(C, x_bar, z_bar, lbda, z, n, r);
        update_xy(x, x_bar, lbda, n);
        update_xy(y, y_bar, 2.0*lbda, m);

    }
    gettimeofday(&requestEnd, 0);

    free(prev_x);
    free(x_bar);
    free(y_bar);
    free(z_bar);

    mkl_res.time =(requestEnd.tv_sec - requestStart.tv_sec)*1E6 + (requestEnd.tv_usec - requestStart.tv_usec);
    mkl_res.loops = count;

    return mkl_res;
}

int main() {
    MKL_INT  m, n, r;
    MKL_INT  i;
    MKL_INT count = 0;
    double   *A, *C, *b, *x, *y, *z;
    MKL_INT  len_A, len_C, len_b, len_x, len_y, len_z;

    m = 12;
    n = 3;
    r = n;

    len_A = m*n;
    len_C = n*r;
    len_b = m;
    len_x = n;
    len_y = m;
    len_z = r;

    A = (double *)calloc(len_A, sizeof(double));
    C = (double *)calloc(len_C, sizeof(double));
    b = (double *)calloc(len_b, sizeof(double));
    x = (double *)calloc(len_x, sizeof(double));
    y = (double *)calloc(len_y, sizeof(double));
    z = (double *)calloc(len_z, sizeof(double));
    if( A == NULL || C == NULL || b == NULL || x == NULL || y == NULL || z == NULL  ) {
        printf( "\n Can't allocate memory for arrays\n");
        return 1;
    }

    srand(time(NULL));

    double lbda = 0.0001;
    /*double machine_eps = 1E-8;*/
    double machine_eps = DBL_EPSILON;
    double nav_run_time = 0.0;
    double mkl_run_time = 0.0;
    MKL_INT trials = 10;
    MKL_INT j;
    MKL_INT nav_loops = 0;
    MKL_INT mkl_loops = 0;

    for (j = 0; j < trials; j++) {
        struct nn1_results  mkl_res, nav_res;
        // Initilise A, C, b, x, y, z
        // Replace with Random
        for (i = 0; i < m*n; i++) { A[i] = r2(); }
        for (i = 0; i < n*r; i++) { C[i] = 0.0; }
        for (i = 0; i < m; i++) { b[i] = r2(); }
        for (i = 0; i < n; i++) { x[i] = r2(); }
        for (i = 0; i < m; i++) { y[i] = r2(); }
        for (i = 0; i < r; i++) { z[i] = r2(); }

        nav_res = run_nav(A, C, b, x, y, z, lbda, machine_eps, m, n, r);
        nav_run_time += nav_res.time;
        nav_loops += nav_res.loops;
        mkl_res = run_mkl(A, C, b, x, y, z, lbda, machine_eps, m, n, r);
        mkl_run_time += mkl_res.time;
        mkl_loops += mkl_res.loops;
    }

    printf("NAV Time: %lfms\n", nav_run_time/1E3/trials);
    printf("NAV Loops: %d\n", nav_loops/trials);
    printf("NAV Time per Loops: %lfus\n", nav_run_time/nav_loops);
    printf("MKL Time: %lfms\n", mkl_run_time/1E3/trials);
    printf("MKL Loops: %d\n", mkl_loops/trials);
    printf("MKL Time per Loops: %lfus\n", mkl_run_time/mkl_loops);
    /*printf("x: [");*/
    /*for (i = 0; i < n; i++) { printf("%.6f,\t", x[i]); }*/
    /*printf("]\n");*/

    free(A);
    free(C);
    free(b);
    free(x);
    free(y);
    free(z);

    return 0;
}
