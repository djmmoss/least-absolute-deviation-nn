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
		val y_bar = YNet(x_bar, y, b, ain)
		val z_bar = ZNet(x_bar, z, cin)

		val x_diff = (x_bar, x).zipped.map(_-_).map(ele => ele*%lbda)
		val y_diff = (y_bar, y).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))
		val z_diff = (MatrixVectorMult(cin, x_bar), z_bar).zipped.map(_-_).map(ele => ele*%lbda*%Fixed(2, bitWidth, fracWidth))

		val update_x = (x, x_diff).zipped.map(_+_)
		val update_y = (y, y_diff).zipped.map(_+_)
		val update_z = (z, z_diff).zipped.map(_+_)

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
