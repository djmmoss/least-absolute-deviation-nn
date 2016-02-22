/*
 Copyright (c) 2011, 2012, 2013, 2014 The Regents of the University of
 Sydney. All Rights Reserved.  Redistribution and use in
 source and binary forms, with or without modification, are permitted
 provided that the following conditions are met:

    * Redistributions of source code must retain the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer in the documentation and/or other materials
      provided with the distribution.
    * Neither the name of the Regents nor the names of its contributors
      may be used to endorse or promote products derived from this
      software without specific prior written permission.

 IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF
 ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION
 TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 MODIFICATIONS.
*/

package LAD

import Chisel._

object MatrixVectorMult {
	def apply(a : Vec[Vec[Fixed]], b : Vec[Fixed]) : Vec[Fixed] = {
		checkDIM(a.length, a(0).length, b.length)
        val matrixVectorMultMod = Module(new MatrixVectorMultModule(b(0).getWidth(), b(0).getFractionalWidth(), a.length, b.length))
	    matrixVectorMultMod.io.a := a
	    matrixVectorMultMod.io.b := b
	    matrixVectorMultMod.io.o
    }

	def checkDIM(arow : Int, acol : Int, bdim : Int) = {
		if (acol != bdim) {
			ChiselError.error("Input Matrix and Vector not aligned - Matrix: (" + arow + ", " + acol + "), Vector: " + bdim)
		}
	}
}

class MatrixVectorMultModule(totalWidth : Int, fracWidth : Int, m : Int, n : Int) extends Module {
    val io = new Bundle {
        val a = Vec.fill(m){Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}}
        val b = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
        val o = Vec.fill(m){Fixed(OUTPUT, totalWidth, fracWidth)}
    }
    io.o := Reg(next=Vec(io.a.map(aCol => DotProduct(aCol, io.b))))
}

object MatrixTranspose {
	def apply(a : Vec[Vec[Fixed]]) : Vec[Vec[Fixed]] = Vec(for(i <- 0 until a(0).length) yield Vec(for(j <- 0 until a.length) yield a(j)(i)))
}

object DotProduct {
	def apply(a : Vec[Fixed], b : Vec[Fixed]): Fixed = {
	val dotProd = Module(new DotProductModule(a(0).getWidth(), a(0).getFractionalWidth(), a.length))
    dotProd.io.a := a
    dotProd.io.b := b
    dotProd.io.o
    }
}

class DotProductModule(totalWidth : Int, fracWidth : Int, len : Int) extends Module {
    val io = new Bundle {
        val a = Vec.fill(len){Fixed(INPUT, totalWidth, fracWidth)}
        val b = Vec.fill(len){Fixed(INPUT, totalWidth, fracWidth)}
        val o = Fixed(OUTPUT, totalWidth, fracWidth)
    }
    val op : (Fixed, Fixed) => Fixed = _+_
    io.o := reduceTree((io.a, io.b).zipped.map((aEle, bEle) => Reg(next=aEle*%bEle)).toList, op)
}

object XNet {
  def apply(x : Vec[Fixed],
            y : Vec[Fixed],
            z : Vec[Fixed],
            a : Vec[Vec[Fixed]],
            c : Vec[Vec[Fixed]]) : Vec[Fixed] = {
    val xNet = Module(new XNetModule(x(0).getWidth(), x(0).getFractionalWidth(), y.length, x.length, z.length))
    xNet.io.x := x
    xNet.io.y := y
    xNet.io.z := z
    xNet.io.a := a
    xNet.io.c := c
    xNet.io.o
  }
}

class XNetModule(totalWidth : Int, fracWidth : Int, m : Int, n : Int, r : Int) extends Module {
  val io = new Bundle {
    val x = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
    val y = Vec.fill(m){Fixed(INPUT, totalWidth, fracWidth)}
    val z = Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}
    val a = Vec.fill(m){Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}}
    val c = Vec.fill(n){Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}}
    val o = Vec.fill(n){Fixed(OUTPUT, totalWidth, fracWidth)}
  }
  val ay = MatrixVectorMult(MatrixTranspose(io.a), io.y)
  val cz = Reg(next=MatrixVectorMult(MatrixTranspose(io.c), io.z))
  val x_delay = ShiftRegister(io.x, 2)
  val xay = Reg(next=Vec((x_delay, ay).zipped.map(_-_)))
  val x_stage = Reg(next=Vec((xay, cz).zipped.map(_+_)))
  val gPos = Fixed(2, io.x(0).getWidth(), io.x(0).getFractionalWidth())
  val gNeg = Fixed(-2, io.x(0).getWidth(), io.x(0).getFractionalWidth())
  val x_bar = Vec(x_stage.map(xEle => g(xEle, gPos, gNeg)))
  (io.o, x_bar).zipped.map((out, ele) => out := ele)
}

object YNet {
	def apply(x : Vec[Fixed], y : Vec[Fixed], b : Vec[Fixed], a : Vec[Vec[Fixed]]) : Vec[Fixed] = {
    val yNet = Module(new YNetModule(y(0).getWidth(), y(0).getFractionalWidth(), y.length, x.length))
    yNet.io.x := x
    yNet.io.y := y
    yNet.io.b := b
    yNet.io.a := a
    yNet.io.o
	}
}

class YNetModule(totalWidth : Int, fracWidth : Int, m : Int, n : Int) extends Module {
  val io = new Bundle {
    val x = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
    val y = Vec.fill(m){Fixed(INPUT, totalWidth, fracWidth)}
    val b = Vec.fill(m){Fixed(INPUT, totalWidth, fracWidth)}
    val a = Vec.fill(m){Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}}
    val o = Vec.fill(m){Fixed(OUTPUT, totalWidth, fracWidth)}
  }
  val ax = MatrixVectorMult(io.a, io.x)
  val b_next = ShiftRegister(io.b, 2)
  val y_next = ShiftRegister(io.y, 3)
  val axb = Reg(next=Vec((ax, b_next).zipped.map(_-_)))
  val y_stage = Reg(next=Vec((axb, y_next).zipped.map(_+_)))
  val gPos = Fixed(2, io.y(0).getWidth(), io.y(0).getFractionalWidth())
  val gNeg = Fixed(-2, io.y(0).getWidth(), io.y(0).getFractionalWidth())
  val y_bar = Vec(y_stage.map(yEle => g(yEle, gPos, gNeg)))
  (io.o, y_bar).zipped.map((out, ele) => out := ele)
}

object ZNet {
	def apply(x : Vec[Fixed], z : Vec[Fixed], c : Vec[Vec[Fixed]]) : Vec[Fixed] = {
    val zNet = Module(new ZNetModule(z(0).getWidth(), z(0).getFractionalWidth(), x.length, z.length))
    zNet.io.x := x
    zNet.io.z := z
    zNet.io.c := c
    zNet.io.o
	}
}

class ZNetModule(totalWidth : Int, fracWidth : Int, n : Int, r : Int) extends Module {
  val io = new Bundle {
    val x = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
    val z = Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}
    val c = Vec.fill(n){Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}}
    val o = Vec.fill(r){Fixed(OUTPUT, totalWidth, fracWidth)}
  }
  val cx = MatrixVectorMult(io.c, io.x)
  val z_next = ShiftRegister(io.z, 2)
  val z_stage = Reg(next=Vec((cx, z_next).zipped.map(_-_)))
  val gPos = Fixed(2, io.z(0).getWidth(), io.z(0).getFractionalWidth())
  val gNeg = Fixed(-2, io.z(0).getWidth(), io.z(0).getFractionalWidth())
  val z_bar = Vec(z_stage.map(zEle => g(zEle, gPos, gNeg)))
  (io.o, z_bar).zipped.map((out, ele) => out := ele)
}

object NN1 {
	def apply(x : Vec[Fixed], y : Vec[Fixed], z : Vec[Fixed], b : Vec[Fixed], ain : Vec[Vec[Fixed]], cin : Vec[Vec[Fixed]], lbda : Fixed) : (Vec[Fixed], Vec[Fixed], Vec[Fixed]) = {
		val bitWidth = b(0).getWidth()
		val fracWidth = b(0).getFractionalWidth()

		val x_bar = XNet(x, y, z, ain, cin)
		
        // Pipeline Stage after x_bar
        val x_bar_p1 = Reg(next=x_bar)
        val x_p1 = ShiftRegister(x, 5)
        val y_p1 = ShiftRegister(y, 5)
        val z_p1 = ShiftRegister(z, 5)
        val a_p1 = ShiftRegister(ain, 5)
        val c_p1 = ShiftRegister(cin, 5)
        val b_p1 = ShiftRegister(b, 5)

        val y_bar = YNet(x_bar_p1, y_p1, b_p1, a_p1)

        // Pipeline Stage after y_bar
        val y_bar_p2 = Reg(next=y_bar)
        val x_bar_p2 = ShiftRegister(x_bar_p1,4)
        val x_p2 = ShiftRegister(x_p1,4)
        val y_p2 = ShiftRegister(y_p1,4)
        val z_p2 = ShiftRegister(z_p1,4)
        val a_p2 = ShiftRegister(a_p1,4)
        val c_p2 = ShiftRegister(c_p1,4)
        val b_p2 = ShiftRegister(b_p1,4)

		val z_bar = ZNet(x_bar_p2, z_p2, c_p2)
        
        // Pipeline Stage after z_bar
        val z_bar_p3 = Reg(next=z_bar)
        val x_bar_p3 = ShiftRegister(x_bar_p2,4)
        val y_bar_p3 = ShiftRegister(y_bar_p2,4)
        val x_p3 = ShiftRegister(x_p2,4)
        val y_p3 = ShiftRegister(y_p2,4)
        val z_p3 = ShiftRegister(z_p2,4)
        val a_p3 = ShiftRegister(a_p2,4)
        val c_p3 = ShiftRegister(c_p2,4)
        val b_p3 = ShiftRegister(b_p2,4)

		val x_diff = (x_bar_p3, x_p3).zipped.map(_-_).map(ele => ele*%lbda)
		val y_diff = (y_bar_p3, y_p3).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))
		val z_diff = (MatrixVectorMult(c_p3, x_bar_p3), z_bar_p3).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))
        
        
        // Pipeline Stage after z_bar
        val z_diff_p4 = Reg(next=Vec(z_diff))
        val x_diff_p4 = ShiftRegister(Vec(x_diff), 3)
        val y_diff_p4 = ShiftRegister(Vec(y_diff), 3)
        val x_p4 = ShiftRegister(x_p3, 3)
        val y_p4 = ShiftRegister(y_p3, 3)
        val z_p4 = ShiftRegister(z_p3, 3)

		val update_x = Reg(next=Vec((x_p4, x_diff_p4).zipped.map(_+_)))
		val update_y = Reg(next=Vec((y_p4, y_diff_p4).zipped.map(_+_)))
		val update_z = Reg(next=Vec((z_p4, z_diff_p4).zipped.map(_+_)))

		(Vec(update_x), Vec(update_y), Vec(update_z))
	}
}


class NN1Top(totalWidth : Int, fracWidth : Int, m : Int, n : Int, r : Int) extends Module {
  val io = new Bundle {
    val x = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
    val y = Vec.fill(m){Fixed(INPUT, totalWidth, fracWidth)}
    val z = Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}
    val b = Vec.fill(m){Fixed(INPUT, totalWidth, fracWidth)}
    val a = Vec.fill(m){Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}}
    val c = Vec.fill(n){Vec.fill(r){Fixed(INPUT, totalWidth, fracWidth)}}
    val lbda = Fixed(INPUT, totalWidth, fracWidth)
    val xout = Vec.fill(n){Fixed(OUTPUT, totalWidth, fracWidth)}
    val yout = Vec.fill(m){Fixed(OUTPUT, totalWidth, fracWidth)}
    val zout = Vec.fill(r){Fixed(OUTPUT, totalWidth, fracWidth)}
  }
  val (xout, yout, zout) = NN1(io.x, io.y, io.z, io.b, io.a, io.c, io.lbda)
  io.xout := xout
  io.yout := yout
  io.zout := zout
}

class NN1ShiftTop(totalWidth : Int, fracWidth : Int, m : Int, n : Int, r : Int) extends Module {
    val io = new Bundle {
        val x = Fixed(INPUT, totalWidth, fracWidth)
        val y = Fixed(INPUT, totalWidth, fracWidth)
        val z = Fixed(INPUT, totalWidth, fracWidth)
        val b = Fixed(INPUT, totalWidth, fracWidth)
        val a = Fixed(INPUT, totalWidth, fracWidth)
        val c = Fixed(INPUT, totalWidth, fracWidth)
        val lbda = Fixed(INPUT, totalWidth, fracWidth)
        val xout = Fixed(OUTPUT, totalWidth, fracWidth)
        val yout = Fixed(OUTPUT, totalWidth, fracWidth)
        val zout = Fixed(OUTPUT, totalWidth, fracWidth)
    }
    val nnTop = Module(new NN1Top(totalWidth, fracWidth, m, n, r))

    nnTop.io.lbda := io.lbda
    
    // B Input
    val bShift = Vec.fill(m){Reg(init=Fixed(0, totalWidth, fracWidth))}
    for (i <- 0 until m-1) {
        bShift(i) := bShift(i+1)
        nnTop.io.b(i) := bShift(i)
    }
    bShift(m-1) := io.b
    nnTop.io.b(m-1) := io.b

    // X Input
    val xShift = Vec.fill(n){Reg(init=Fixed(0, totalWidth, fracWidth))}
    for (i <- 0 until n-1) {
        xShift(i) := xShift(i+1)
        nnTop.io.x(i) := xShift(i)
    }
    xShift(n-1) := io.x
    nnTop.io.x(n-1) := io.x

    // Y Input
    val yShift = Vec.fill(m){Reg(init=Fixed(0, totalWidth, fracWidth))}
    for (i <- 0 until m-1) {
        yShift(i) := yShift(i+1)
        nnTop.io.y(i) := yShift(i)
    }
    yShift(m-1) := io.y
    nnTop.io.y(m-1) := io.y

    // Z Input
    val zShift = Vec.fill(r){Reg(init=Fixed(0, totalWidth, fracWidth))}
    for (i <- 0 until r-1) {
        zShift(i) := zShift(i+1)
        nnTop.io.z(i) := zShift(i)
    }
    zShift(r-1) := io.z
    nnTop.io.z(r-1) := io.z

    // A Input
    val aShift = Vec.fill(m){Vec.fill(n){Reg(init=Fixed(0, totalWidth, fracWidth))}}
    for (i <- 0 until m-1) {
        aShift(i) := aShift(i+1)
        nnTop.io.a(i) := aShift(i)
    }
    for (i <- 0 until n) {
        aShift(m-1)(i) := io.a
        nnTop.io.a(m-1)(i) := io.a
    }

    // C Input
    val cShift = Vec.fill(n){Vec.fill(r){Reg(init=Fixed(0, totalWidth, fracWidth))}}
    for (i <- 0 until n-1) {
        cShift(i) := cShift(i+1)
        nnTop.io.c(i) := cShift(i)
    }
    for (i <- 0 until r) {
        cShift(n-1)(i) := io.c
        nnTop.io.c(n-1)(i) := io.c
    }
    
    val nnxout = Reg(next=nnTop.io.xout.reduce(_+_))
    val nnyout = Reg(next=nnTop.io.yout.reduce(_+_))
    val nnzout = Reg(next=nnTop.io.zout.reduce(_+_))

    io.xout := nnxout
    io.yout := nnyout
    io.zout := nnzout
}
