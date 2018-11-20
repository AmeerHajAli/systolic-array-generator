
package systolic

import chisel3._
import chisel3.iotesters._

import scala.collection.mutable

class GridUnitTest(c: Grid, m1: Seq[Seq[Int]], m2: Seq[Seq[Int]]) extends PeekPokeTester(c) {
  def generateA(m: Seq[Seq[Int]]): Seq[Seq[Int]] = {
    val numEltsInRow = m(0).length * math.ceil(m.length / (c.gridRows * c.meshRows).toDouble).toInt
    (0 until c.gridRows*c.meshRows).map { r =>
      val unPaddedA = m.drop(r).grouped(c.meshRows*c.gridRows).map(_.head).toList
      val paddedA =
        Seq.fill(r / c.meshRows)(0) ++
        unPaddedA.flatten ++
        Seq.fill(numEltsInRow - (r / c.meshRows) - unPaddedA.flatten.length)(0)
      paddedA
    }
  }
  val A = generateA(m1)

  def generateB(m: Seq[Seq[Int]]): Seq[Seq[Int]] = {
    val mT = m.transpose
    val numEltsInCol = mT(0).length * math.ceil(mT.length / (c.gridColumns * c.meshColumns).toDouble).toInt
    (0 until c.gridColumns * c.meshColumns).map { r =>
      val unPaddedB = mT.drop(r).grouped(c.meshColumns * c.gridColumns).map(_.head).toList
      val paddedB =
        Seq.fill(r / c.meshColumns)(0) ++
          unPaddedB.flatten ++
          Seq.fill(numEltsInCol - (r / c.meshColumns) - unPaddedB.flatten.length)(0)
      paddedB
    }
  }
  val B = generateB(m2)

  def generateS: Seq[Seq[Int]] = {
    (0 until c.gridColumns*c.meshColumns).map { i =>
      Seq.fill(c.gridColumns*c.meshColumns)(0) ++
        Seq.fill(c.meshRows*c.gridRows + 0)(1) ++
        Seq.fill(c.meshRows*c.gridRows)(0)
    }
  }
  val S = generateS

  def mult[A](a: Seq[Seq[A]], b: Seq[Seq[A]])(implicit n: Numeric[A]) = {
    import n._
    for (row <- a)
      yield for(col <- b.transpose)
        yield row zip col map Function.tupled(_*_) reduceLeft (_+_)
  }
  Predef.println(mult(m1, m2))
/*
  def generateC(cGold: Array[Array[Int]]): Array[Array[Tuple2[Int, Boolean]]]= {
    val cGoldT = cGold.transpose
    (0 until c.columns).map { i =>
      Array.fill(c.rows )((0, false)) ++ cGoldT(i).reverse.map((_, true)) ++ Array.fill(c.rows - 1)((0, false))
    }.toArray
  }
  */

  def print2DArray[A](a: Array[Array[A]]): Unit = {
    a.map(_.mkString(", ")).foreach {
      line => println(line)
    }
  }

  reset()
  c.io.in_s_vec.foreach { col =>
    col.foreach { colMesh =>
      poke(colMesh, 0)
    }
  }

  val Apad = A.map(_.padTo(S(0).length, 0))
  val Bpad = B.map(_.padTo(S(0).length, 0))
  val Agrouped = Apad.grouped(c.meshRows).toList
  val Bgrouped = Bpad.grouped(c.meshColumns).toList
  val Sgrouped = S.grouped(c.gridColumns).toList
  Predef.println(Apad)
  Predef.println(Bpad)
  Predef.println(S)
  def strobeInputs(cycle: Int): Unit = {
    for (gridRow <- 0 until c.gridRows) {
      for (meshRow <- 0 until c.meshRows) {
        poke(c.io.in_a_vec(gridRow)(meshRow), Agrouped(gridRow)(meshRow)(cycle))
      }
    }
    for (gridCol <- 0 until c.gridColumns) {
      for (meshCol <- 0 until c.meshColumns) {
        poke(c.io.in_b_vec(gridCol)(meshCol), Bgrouped(gridCol)(meshCol)(cycle))
        poke(c.io.in_s_vec(gridCol)(meshCol), Sgrouped(gridCol)(meshCol)(cycle))
      }
    }
  }

  println("Peeking output out_vec")
  for (cycle <- 0 until Apad(0).length) {
    strobeInputs(cycle)
    val peeked = peek(c.io.out_vec)
    step(1)

    println(peeked.map(_.toString).reduce(_ + "\t" + _))
    Predef.println(peek(c.io.out_s_vec).map(_.toString))
  }
}

class GridTester extends ChiselFlatSpec {
  /*
  val m1 = Seq(
    Seq(10, 3),
    Seq(2, 13)
  )
  */
  // 6x4
  val m1 = Seq(
    Seq(1, 2, 3, 4),
    Seq(5, 6, 7, 8),
    Seq(9, 10, 11, 12),
    Seq(13, 14, 15, 16),
    Seq(17, 18, 19, 20),
    Seq(21, 22, 23, 24)
  )

  // 4x2
  val m2 = Seq(
    Seq(1, 2),
    Seq(3, 4),
    Seq(5, 6),
    Seq(7, 8)
  )
  "GridTester" should "run matmul using a 2x2 grid with 2x2 meshes" in {
    iotesters.Driver.execute(
      Array("--backend-name", "treadle", "--generate-vcd-output", "on"),
      () => new Grid(16, 3, 2, 2, 2))
    {
      c => new GridUnitTest(c, m1, m2)
    } should be (true)
  }
}