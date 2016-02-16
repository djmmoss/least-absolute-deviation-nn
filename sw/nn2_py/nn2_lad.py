import numpy as np
import time

m, n, r = 12, 3, 3

lbda = 0.01

A = np.array([[-1, 1, 0.0],
                 [-0.5, 1, -0.5],
                 [0.5, -1, 1],
                 [1, -1, 1],
                 [1, -0.5, 0.5],
                 [2, -1, 1],
                 [1, -1, 1],
                 [0.5, -1, 1],
                 [0.5, -0.75, 1],
                 [2, -2, 3],
                 [0, -1, 1],
                 [1 ,-1, 3]])


b = np.array([-1, -0.5, 1, 1, 0.5, 1, 0, -0.5, -0.25, -3, 0, -2])

## Constraint 0 <= 2x1 - x2 <= 2
C = np.array([[0, 0, 0],
	      [0, 0, 0],
	      [0, 0, 0]])

def g(x, rho=-10, qi=10):
        if x < rho:
                return rho
        if x > qi:
                return qi
        return x


## Initial Values
x = np.random.randn(n)
y = np.random.randn(m)
z = np.random.randn(r)


prev_x = np.zeros(n)

i = 0

start = time.time()
while(sum(pow(x - prev_x, 2)) > np.finfo(float).eps):
    i += 1
    prev_x = x

    y_stage = y + np.dot(A, x) - b
    y_bar = [g(ele) for ele in y_stage]

    z_stage = np.dot(C, x) - z
    z_bar = [g(ele) for ele in z_stage]

    x_stage = x - np.dot(np.transpose(A), y_bar) + np.dot(np.transpose(C), z - np.dot(C, x) + z_bar)
    x_bar = [g(ele) for ele in x_stage]

    x_diff = 2*lbda*(x_bar - x)
    y_diff = lbda*(y_bar - y)
    z_diff = lbda*(np.dot(C, x_bar) - z_bar)

    x = x + x_diff
    y = y + y_diff
    z = z + z_diff


end = time.time()
print str(end - start) + 's ' + str(i) + 'loops'
print x
print sum((np.dot(A,x) - b)**2)
