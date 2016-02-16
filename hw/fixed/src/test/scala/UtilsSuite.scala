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

class UtilsSuite extends TestSuite {
 
  val trials = 100
  val totalWidth = 16
  val fracWidth = 8

  @Test def testg() {
    class gTest extends Module {
      val io = new Bundle {
        val in = Fixed(INPUT, totalWidth, fracWidth)
        val hi = Fixed(INPUT, totalWidth, fracWidth)
        val lo = Fixed(INPUT, totalWidth, fracWidth)
        val out = Fixed(OUTPUT, totalWidth, fracWidth)
      }
      io.out := g(io.in, io.hi, io.lo)
    }

    class gTests(c : gTest) extends Tester(c) {
      for (i <- 0 until trials) {
        val in = BigInt(rnd.nextInt(1 << totalWidth - 1)) * scala.math.pow(-1, rnd.nextInt(2)).toInt
        val hi = BigInt(rnd.nextInt(1 << totalWidth - 1))
        val lo = hi * -1
        poke(c.io.in, in)
        poke(c.io.hi, hi)
        poke(c.io.lo, lo)
        val expected = if (in > hi) hi else if (in < lo) lo else in
        expect(c.io.out, expected)
      }
    }

    launchCppTester((c : gTest) => new gTests(c))
  }
}
