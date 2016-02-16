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

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.junit.Ignore


import Chisel._
import LAD._

class NN1Suite extends TestSuite {
 
  def toFixedT(x : Double, fracWidth : Int) : BigInt = BigInt((x*scala.math.pow(2, fracWidth)).toInt)
  def toFixed(x : Double, fracWidth : Int) : BigInt = BigInt(scala.math.round(x*scala.math.pow(2, fracWidth)))
  def toDouble(x : BigInt, fracWidth : Int) : Double = {
    val v = if (x.testBit(totalWidth - 1)) F(-(1 << (totalWidth - fracWidth))) | x else x
    v.toDouble/scala.math.pow(2, fracWidth)
  }
  def F(x : Double) : BigInt = toFixed(x, fracWidth)
  def F(x : Int) : BigInt = F(x.toDouble)


  val trials = 10
  val totalWidth = 18
  val fracWidth = 14
  val m = 12
  val n = 4
  val r = 3

  @Test def testMatrixVectorMult() {
    class MatrixVectorMultTest extends Module {
      val io = new Bundle {
        val a = Vec.fill(m){Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}}
        val b = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
        val out = Vec.fill(m){Fixed(OUTPUT, totalWidth, fracWidth)}
      }
      io.out := MatrixVectorMult(io.a, io.b)
    }

    class MatrixVectorMultTests(c : MatrixVectorMultTest) extends Tester(c) {
      for (i <- 0 until trials) {
        val a = List.fill(m){List.fill(n){BigInt(rnd.nextInt(1 << totalWidth - 2)) * scala.math.pow(-1, rnd.nextInt(2)).toInt}}
        val b = List.fill(n){BigInt(rnd.nextInt(1 << totalWidth - 2)) * scala.math.pow(-1, rnd.nextInt(2)).toInt}
        (a, c.io.a).zipped.map((row, in_row) => (row, in_row).zipped.map((ele, in) => poke(in, ele)))
        (b, c.io.b).zipped.map((ele, in) => poke(in, ele))
        val expected = a.map(aCol => (aCol, b).zipped.map((aEle, bEle) => (aEle*bEle) >> fracWidth).reduce(_+_))
        (expected, c.io.out).zipped.map((ele, out) => expect(out, ele))
      }
    }

    launchCppTester((c : MatrixVectorMultTest) => new MatrixVectorMultTests(c))
  }

  @Test def testDotProduct() {
    class DotProductTest extends Module {
      val io = new Bundle {
        val a = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
        val b = Vec.fill(n){Fixed(INPUT, totalWidth, fracWidth)}
        val out = Fixed(OUTPUT, totalWidth, fracWidth)
      }
      io.out := DotProduct(io.a, io.b)
    }

    class DotProductTests(c : DotProductTest) extends Tester(c) {
      for (i <- 0 until trials) {
        val a = List.fill(n){BigInt(rnd.nextInt(1 << totalWidth - 2)) * scala.math.pow(-1, rnd.nextInt(2)).toInt}
        val b = List.fill(n){BigInt(rnd.nextInt(1 << totalWidth - 2)) * scala.math.pow(-1, rnd.nextInt(2)).toInt}
        (a, c.io.a).zipped.map((ele, in) => poke(in, ele))
        (b, c.io.b).zipped.map((ele, in) => poke(in, ele))
        val expected = (a, b).zipped.map((aEle, bEle) => (aEle*bEle) >> fracWidth).sum
        expect(c.io.out, expected)
      }
    }

    launchCppTester((c : DotProductTest) => new DotProductTests(c))
  }

  @Test def testMatrixTranspose() {
    class MatrixTransposeTest extends Module {
      val io = new Bundle {
        val a = Vec.fill(2){Vec.fill(4){Fixed(INPUT, totalWidth, fracWidth)}}
        val out = Vec.fill(4){Vec.fill(2){Fixed(OUTPUT, totalWidth, fracWidth)}}
      }
      io.out := MatrixTranspose(io.a)
    }

    class MatrixTransposeTests(c : MatrixTransposeTest) extends Tester(c) {
      for (i <- 0 until trials) {
        val a = List(List(1, 2, 3, 4), List(5, 6, 7, 8))
        val expected = List(List(1, 5), List(2, 6), List(3, 7), List(4, 8))
        (a, c.io.a).zipped.map((row, in_row) => (row, in_row).zipped.map((ele, in) => poke(in, ele)))
        (expected, c.io.out).zipped.map((eleCol, outCol) => (eleCol, outCol).zipped.map((ele, out) => expect(out, ele)))
      }
    }

    launchCppTester((c : MatrixTransposeTest) => new MatrixTransposeTests(c))
  }

  @Test def testNN1() {
    class NN1Test extends Module {
      val io = new Bundle {
        val x = Vec.fill(3){Fixed(INPUT, totalWidth, fracWidth)}
        val y = Vec.fill(12){Fixed(INPUT, totalWidth, fracWidth)}
        val z = Vec.fill(3){Fixed(INPUT, totalWidth, fracWidth)}
        val b = Vec.fill(12){Fixed(INPUT, totalWidth, fracWidth)}
        val a = Vec.fill(12){Vec.fill(3){Fixed(INPUT, totalWidth, fracWidth)}}
        val c = Vec.fill(3){Vec.fill(3){Fixed(INPUT, totalWidth, fracWidth)}}
        val lbda = Fixed(INPUT, totalWidth, fracWidth)
        val xout = Vec.fill(3){Fixed(OUTPUT, totalWidth, fracWidth)}
        val yout = Vec.fill(12){Fixed(OUTPUT, totalWidth, fracWidth)}
        val zout = Vec.fill(3){Fixed(OUTPUT, totalWidth, fracWidth)}
      }
      val (xout, yout, zout) = NN1(io.x, io.y, io.z, io.b, io.a, io.c, io.lbda)
      io.xout := xout
      io.yout := yout
      io.zout := zout
    }

    class NN1Tests(c : NN1Test) extends Tester(c) {
      val a = List(List(F(-1), F(1.0), F(0.0)),
               List(F(-0.5), F(1), F(-0.5)),
               List(F(0.5), F(-1), F(1)),
               List(F(1), F(-1), F(1)),
               List(F(1), F(-0.5), F(0.5)),
               List(F(2), F(-1), F(1)),
               List(F(1), F(-1), F(1)),
               List(F(0.5), F(-1), F(1)),
               List(F(0.5), F(-0.75), F(1)),
               List(F(2), F(-2), F(3)),
               List(F(0), F(-1), F(1)),
               List(F(1) ,F(-1), F(3)))
      val cin = List(List(0, 0, 0), List(0, 0, 0), List(0, 0, 0))
      val b = List(F(-1), F(-0.5), F(1), F(1), F(0.5), F(1), F(0), F(-0.5), F(-0.25), F(-3), F(0), F(-2))
      val lbda = F(0.01)
      var stop = false
      var x : List[BigInt] = List(F(0.5), F(0.5), F(0.5))
      var y : List[BigInt] = List(F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5), F(0.5))
      var z : List[BigInt] = List(0, 0, 0)
      (b, c.io.b).zipped.map((ele, in) => poke(in, ele))
      (a, c.io.a).zipped.map((row, in_row) => (row, in_row).zipped.map((ele, in) => poke(in, ele)))
      (cin, c.io.c).zipped.map((row, in_row) => (row, in_row).zipped.map((ele, in) => poke(in, ele)))
      poke(c.io.lbda, lbda)
      while (!stop) {
        val prevX = x
        (x, c.io.x).zipped.map((ele, in) => poke(in, ele))
        (y, c.io.y).zipped.map((ele, in) => poke(in, ele))
        (z, c.io.z).zipped.map((ele, in) => poke(in, ele))
        step(1)
        x = c.io.xout.toList.map(ele => peek(ele))
        y = c.io.yout.toList.map(ele => peek(ele))
        z = c.io.zout.toList.map(ele => peek(ele))
        stop = (prevX, x).zipped.map((pEle, ele) => pEle == ele).reduce(_&_)
      }
      x.map(ele => println(toDouble(ele, fracWidth)))
    }

    launchCppTester((c : NN1Test) => new NN1Tests(c))
  }

}
