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
		Vec(a.map(aCol => DotProduct(aCol, b)))
	}

	def checkDIM(arow : Int, acol : Int, bdim : Int) = {
		if (acol != bdim) {
			ChiselError.error("Input Matrix and Vector not aligned - Matrix: (" + arow + ", " + acol + "), Vector: " + bdim)
		}
	}
}

object MatrixTranspose {
	def apply(a : Vec[Vec[Fixed]]) : Vec[Vec[Fixed]] = Vec(for(i <- 0 until a(0).length) yield Vec(for(j <- 0 until a.length) yield a(j)(i)))
}

object DotProduct {
	def apply(a : Vec[Fixed], b : Vec[Fixed]): Fixed = {
		val op : (Fixed, Fixed) => Fixed = _+_
		reduceTree((a, b).zipped.map((aEle, bEle) => aEle*%bEle).toList, op)
	}
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
  val cz = MatrixVectorMult(MatrixTranspose(io.c), io.z)
  val xay = (io.x, ay).zipped.map(_-_)
  val x_stage = (xay, cz).zipped.map(_+_)
  val gPos = Fixed(2, io.x(0).getWidth(), io.x(0).getFractionalWidth())
  val gNeg = Fixed(-2, io.x(0).getWidth(), io.x(0).getFractionalWidth())
  val x_bar = Vec(x_stage.map(xEle => g(xEle, gPos, gNeg)))
  (io.o, x_bar).zipped.map((out, ele) => out := ele)
}

object YNet {
	def apply(x : Vec[Fixed], y : Vec[Fixed], b : Vec[Fixed], a : Vec[Vec[Fixed]]) : Vec[Fixed] = {
		val ax = MatrixVectorMult(a, x)
		val axb = (ax, b).zipped.map(_-_)
		val y_stage = (axb, y).zipped.map(_+_)
		val gPos = Fixed(2, y(0).getWidth(), y(0).getFractionalWidth())
		val gNeg = Fixed(-2, y(0).getWidth(), y(0).getFractionalWidth())
		Vec(y_stage.map(yEle => g(yEle, gPos, gNeg)))
	}
}

object ZNet {
	def apply(x : Vec[Fixed], z : Vec[Fixed], c : Vec[Vec[Fixed]]) : Vec[Fixed] = {
		val cx = MatrixVectorMult(c, x)
		val z_stage = (cx, z).zipped.map(_-_)
		val gPos = Fixed(2, z(0).getWidth(), z(0).getFractionalWidth())
		val gNeg = Fixed(-2, z(0).getWidth(), z(0).getFractionalWidth())
		Vec(z_stage.map(zEle => g(zEle, gPos, gNeg)))
	}
}

object NN1 {
	def apply(x : Vec[Fixed], y : Vec[Fixed], z : Vec[Fixed], b : Vec[Fixed], ain : Vec[Vec[Fixed]], cin : Vec[Vec[Fixed]], lbda : Fixed) : (Vec[Fixed], Vec[Fixed], Vec[Fixed]) = {
		val bitWidth = b(0).getWidth()
		val fracWidth = b(0).getFractionalWidth()

		val x_bar = XNet(x, y, z, ain, cin)
		
        // Pipeline Stage after x_bar
        val x_bar_p1 = Reg(next=x_bar)
        val x_p1 = Reg(next=x)
        val y_p1 = Reg(next=y)
        val z_p1 = Reg(next=z)
        val a_p1 = Reg(next=ain)
        val c_p1 = Reg(next=cin)
        val b_p1 = Reg(next=b)

        val y_bar = YNet(x_bar_p1, y_p1, b_p1, a_p1)

        // Pipeline Stage after y_bar
        val x_bar_p2 = Reg(next=x_bar_p1)
        val y_bar_p2 = Reg(next=y_bar)
        val x_p2 = Reg(next=x_p1)
        val y_p2 = Reg(next=y_p1)
        val z_p2 = Reg(next=z_p1)
        val a_p2 = Reg(next=a_p1)
        val c_p2 = Reg(next=c_p1)
        val b_p2 = Reg(next=b_p1)

		val z_bar = ZNet(x_bar_p2, z_p2, c_p2)
        
        // Pipeline Stage after z_bar
        val x_bar_p3 = Reg(next=x_bar_p2)
        val y_bar_p3 = Reg(next=y_bar_p2)
        val z_bar_p3 = Reg(next=z_bar)
        val x_p3 = Reg(next=x_p2)
        val y_p3 = Reg(next=y_p2)
        val z_p3 = Reg(next=z_p2)
        val a_p3 = Reg(next=a_p2)
        val c_p3 = Reg(next=c_p2)
        val b_p3 = Reg(next=b_p2)

		val x_diff = (x_bar_p3, x_p3).zipped.map(_-_).map(ele => ele*%lbda)
		val y_diff = (y_bar_p3, y_p3).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))
		val z_diff = (MatrixVectorMult(c_p3, x_bar_p3), z_bar_p3).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))
        
        
        // Pipeline Stage after z_bar
        val x_diff_p4 = Reg(next=Vec(x_diff))
        val y_diff_p4 = Reg(next=Vec(y_diff))
        val z_diff_p4 = Reg(next=Vec(z_diff))
        val x_p4 = Reg(next=x_p3)
        val y_p4 = Reg(next=y_p3)
        val z_p4 = Reg(next=z_p3)

		val update_x = (x_p4, x_diff_p4).zipped.map(_+_)
		val update_y = (y_p4, y_diff_p4).zipped.map(_+_)
		val update_z = (z_p4, z_diff_p4).zipped.map(_+_)

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
