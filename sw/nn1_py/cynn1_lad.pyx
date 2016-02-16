import cython
cimport cython

import numpy as np
cimport numpy as np

cdef extern from "nn1_lad_c.h":
    int nn1_lad_c(double *A, double *C, double *b, double *x, double *y, double *z, double lbda, double machine_eps, size_t m, size_t n, size_t r)

def cnn1_lad(np.ndarray[np.float64_t, ndim=2, mode='c'] A,
             np.ndarray[np.float64_t, ndim=2, mode='c'] C,
             np.ndarray[np.float64_t, ndim=1, mode='c'] b,
             np.ndarray[np.float64_t, ndim=1, mode='c'] x,
             np.ndarray[np.float64_t, ndim=1, mode='c'] y,
             np.ndarray[np.float64_t, ndim=1, mode='c'] z,
             np.float64_t lbda,
             np.float64_t machine_eps,
             unsigned int m,
             unsigned int n,
             unsigned int r):
    return nn1_lad_c(<double*> A.data,
                    <double*> C.data,
                    <double*> b.data,
                    <double*> x.data,
                    <double*> y.data,
                    <double*> z.data,
                     lbda, machine_eps, m, n, r)

