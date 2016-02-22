package LAD

import Chisel._

object g {		
	def apply[T <: Bits with Num[T]](in : T, hi : T, lo : T) : T = MuxCase(in, Array(
		(in > hi) -> hi,
		(in < lo) -> lo
		))

    def apply(in : Fixed, hi : Fixed, lo : Fixed) : Fixed = {
        val gMod = Module(new gModule(in.getWidth(), in.getFractionalWidth()))
        gMod.io.in := in
        gMod.io.hi := hi
        gMod.io.lo := lo
        gMod.io.ou
    }
}

class gModule(totalWidth : Int, fracWidth : Int) extends Module {
    val io = new Bundle {
        val in = Fixed(INPUT, totalWidth, fracWidth)
        val hi = Fixed(INPUT, totalWidth, fracWidth)
        val lo = Fixed(INPUT, totalWidth, fracWidth)
        val ou = Fixed(OUTPUT, totalWidth, fracWidth)
    }
    io.ou := MuxCase(io.in, Array(
        (io.in > io.hi) -> io.hi,
        (io.in < io.lo) -> io.lo
        ))
}

object reduceTree {
  def apply(list : List[Fixed], op :(Fixed, Fixed) => Fixed) : Fixed = buildTree(List(list), op).last.head

  private def group(list : List[Fixed], size : Int) : List[List[Fixed]] = list.foldLeft( (List[List[Fixed]](), 0) ) { (r, c) =>
    r match {
      case (head :: tail, num) =>
        if (num < size) ( (c :: head) :: tail, num + 1)
        else            ( List(c) :: head :: tail, 1)
    case (Nil, num) => (List(List(c)),1)
    }
  }._1.foldLeft(List[List[Fixed]]())( (r,c) => c.reverse :: r)

  private def buildLevel(list : List[Fixed], op : (Fixed, Fixed) => Fixed) : List[Fixed] = group(list, 2).map(l => l.reduce[Fixed](op(_,_)))

  private def buildTree(list : List[List[Fixed]], op : (Fixed, Fixed) => Fixed) : List[List[Fixed]] = {
    val stage = buildLevel(list.last, op)
    val tree : List[List[Fixed]] = list :+ stage
    if (stage.length == 1) tree else buildTree(tree, op)
  }
}

object exp {

  // Lookup Table with Linear Interpolation
  // Special shout-out to Stephen Tridgell for the basic outline

  def apply(in : Fixed) : Fixed = {
    val bitWidth = in.getWidth()
    val fracWidth = in.getFractionalWidth()
    val (gradTable, offTable) = lookupTables(1.0, 8, fracWidth, bitWidth)
    val x_int = in(bitWidth - 1, fracWidth)
    val FRAC = Fixed(BigInt((1 << fracWidth) - 1), bitWidth, fracWidth)
    val x_frac = in & FRAC
    val x_tabl = in(fracWidth - 1, fracWidth - 8)
    val gradTabOut = gradTable(x_tabl)
    val offTabOut = offTable(x_tabl)
    val gradOut = gradTabOut*%x_frac
    val limit = {
    if ( bitWidth - fracWidth <= log2Up(fracWidth) )
      Bool(false)
    else
      (x_int >= UInt(limitShift(fracWidth), width=(bitWidth - fracWidth)))
    }
    val xint2Out = Mux(limit, UInt(limitShift(fracWidth), width=(log2Up(fracWidth) + 1)),
    x_int(scala.math.min(log2Up(fracWidth), bitWidth - fracWidth - 1), 0) & UInt(limitShift(fracWidth), width=(log2Up(fracWidth) + 1)))
    val yFracOut = gradOut + offTabOut
    yFracOut >> xint2Out
  }

  private def limitShift(fracWidth : Int) : BigInt = { BigInt((1 << log2Up(fracWidth)) - 1) }

  private def lookupTables(end : Double, size : Int, fracWidth : Int, bitWidth : Int) : (Vec[Fixed], Vec[Fixed]) = {
    val gradients = new scala.collection.mutable.ArrayBuffer[Int]()
    val offsets = new scala.collection.mutable.ArrayBuffer[Int]()
    val increment = end / (1 << size)
    var x = 0.0
    while (x < end) {
      val m = -(scala.math.pow(2, - x) - scala.math.pow(2,- x - increment))/increment
      gradients += (m * (1 << fracWidth)).toInt
      val b = scala.math.pow(2, - x) - m*x
      offsets += (b * (1 << fracWidth)).toInt
      x += increment
    }
    (Vec(gradients.map((i: Int) => Fixed(BigInt(i), bitWidth, fracWidth))), Vec(offsets.map((i: Int) => Fixed(BigInt(i), bitWidth, fracWidth))))
  }
}
