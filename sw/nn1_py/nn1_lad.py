from cvxopt import lapack, solvers, matrix, spdiag, log, div, normal
from cvxopt.modeling import variable, op, max, sum
solvers.options['show_progress'] = 0
import numpy as np
import pylab
import time
from cynn1_lad import cnn1_lad

m, n, r = 12, 3, 3

lbda = 0.001

#A = np.array([[-1, 1, 0.0],
                 #[-0.5, 1, -0.5],
                 #[0.5, -1, 1],
                 #[1, -1, 1],
                 #[1, -0.5, 0.5],
                 #[2, -1, 1],
                 #[1, -1, 1],
                 #[0.5, -1, 1],
                 #[0.5, -0.75, 1],
                 #[2, -2, 3],
                 #[0, -1, 1],
                 #[1 ,-1, 3]], dtype=np.double, order='C', copy=False)


#b = np.array([-1, -0.5, 1, 1, 0.5, 1, 0, -0.5, -0.25, -3, 0, -2], dtype=np.double, order='C', copy=False)

## Constraint 0 <= 2x1 - x2 <= 2
#C = np.array([[0, 0, 0],
			  #[0, 0, 0],
			  #[0, 0, 0]], dtype=np.double, copy=False)


def nn1_np(A, C, b, x, y, z, lbda, machine_eps):
    i = 0
    prev_x = np.array(np.zeros(n), dtype=np.double, order='C', copy=False)
    while(sum(pow(x - prev_x, 2)) > machine_eps):
        i += 1
        prev_x = x
        x_stage = x - np.dot(np.transpose(A), y) + np.dot(np.transpose(C), z)
        x_bar = [g(ele) for ele in x_stage]

        y_stage = y + np.dot(A, x_bar) - b
        y_bar = [g(ele) for ele in y_stage]

        z_stage = np.dot(C, x_bar) - z
        z_bar = [g(ele) for ele in z_stage]

        x_diff = lbda*(x_bar - x)
        y_diff = 2*lbda*(y_bar - y)
        z_diff = 2*lbda*(np.dot(C, x_bar) - z_bar)

        x = x + x_diff
        y = y + y_diff
        z = z + z_diff

    return (i, x)


def solver(A, b):
    A = matrix(A)
    b = matrix(b)
    x = variable(n)
    start = time.time()
    op(sum(abs(A*x-b))).solve()
    end = time.time()
    sol_x = np.array(x.value).flatten()

    return ((end - start), sol_x)

def g(in_x, rho=-2, qi=2):
        if in_x < rho:
                return rho
        if in_x > qi:
                return qi
        return in_x

i_np = 0
i_ccc = 0
total_nn1_time = 0
total_c_time = 0
total_solve_time = 0
total_err = 0.0
max_err = 0.0
machine_eps = 1e-8

for j in xrange(0, 2):

    A = np.array(np.random.randn(m, n), dtype=np.double, order='C', copy=False)
    b = np.array(np.random.randn(m), dtype=np.double, order='C', copy=False)
    C = np.zeros((n, r), dtype=np.double, order='C')

    ## Initial Values
    x = np.array(np.random.randn(n), dtype=np.double, order='C', copy=False)
    y = np.array(np.random.randn(m), dtype=np.double, order='C', copy=False)
    z = np.array(np.random.randn(r), dtype=np.double, order='C', copy=False)

    start = time.time()
    (i_n, x_n) = nn1_np(A, C, b, x, y, z, lbda, machine_eps)
    end = time.time()
    i_np += i_n
    total_nn1_time += (end - start)

    start = time.time()
    i_cc = cnn1_lad(A, C, b, x, y, z, lbda, machine_eps, m, n, r)
    end = time.time()
    i_ccc += i_cc
    total_c_time += (end - start)


    (solver_time, sol_x) = solver(A, b)
    total_solve_time += solver_time

    sol_obj =  np.sum(np.abs(np.dot(A, sol_x) - b))
    sol_x =  np.sum(np.abs(np.dot(A, x_n) - b))

    curr_err = np.abs(sol_obj - sol_x)/((sol_obj + sol_x)/2) * 100
    total_err += curr_err
    if (max_err < curr_err):
        max_err = curr_err

j += 1
avg_err = total_err/float(j)
print "*** Accuracy ***"
print '\tAverage Error: ' + str(avg_err) + '%'
print '\tMax Error: ' + str(max_err) + '%'

avg_time = total_solve_time/float(j)
print "*** Results Solver ***"
print '\tAverage Problem Time: ' + str(avg_time * 1e3) + 'ms'
print '\tTime 50 Problems: ' + str(avg_time * 50.0 * 1e3) + 'ms'

avg_time = total_nn1_time/float(j)
avg_loops = i_np/j
print "*** Results NN1 Numpy ***"
print '\tAverage Problem Time: ' + str(avg_time * 1e3) + 'ms'
print '\tAverage # Loops: ' + str(avg_loops)
print '\tAverage Time per Loop: ' + str(avg_time/avg_loops * 1e3) + ' ms'
print '\tTime for 50 Problems: ' + str(avg_time * 50.0 * 1e3) + 'ms'

avg_time = total_c_time/float(j)
avg_loops = i_ccc/j
print "*** Results NN1 C ***"
print '\tAverage Problem Time: ' + str(avg_time * 1e3) + 'ms'
print '\tAverage # Loops: ' + str(avg_loops)
print '\tAverage Time per Loop: ' + str(avg_time/avg_loops * 1e3) + ' ms'
print '\tTime for 50 Problems: ' + str(avg_time * 50.0 * 1e3) + 'ms'

clk_period = 5e-9
pipeline_depth = 50
latency_single_loop = clk_period*pipeline_depth
latency_single_problem = latency_single_loop*avg_loops

print "*** Approx. Results FPGA NN1 ***"
print '\tTime per Loop: ' + str(latency_single_loop * 1e3) + ' ms'
print '\tTime per Problem: ' + str(latency_single_problem * 1e3) + ' ms'
print '\tTime for 50 Problems: ' + str((latency_single_problem + latency_single_loop*50) * 1e3) + ' ms'
