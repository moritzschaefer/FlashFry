/*
 * Copyright (c) 2015 Aaron McKenna
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package standards

import scala.util.matching.Regex

/**
  * everything we need to know about an enzyme
  */
sealed trait ParameterPack {
  def enzyme: Enzyme

  def pam: Array[String]

  def fwdRegex: Regex

  def revRegex: Regex

  def totalScanLength: Int

  def comparisonBitEncoding: Long

  def fivePrimePam: Boolean
}

object ParameterPack {
  def nameToParameterPack(name: String): ParameterPack = name.toUpperCase match {
    case "CPF1"   => Cpf1ParameterPack
    case "SPCAS9" => Cas9ParameterPack
    case "SPCAS9NGG" => Cas9NGGParameterPack
    case "SPCAS9NAG" => Cas9NAGParameterPack
    case _        => throw new IllegalStateException("Unable to find the correct parameter pack for enzyme: " + name)
  }

  def indexToParameterPack(index: Int): ParameterPack = index match {
    case 1 => Cpf1ParameterPack
    case 2 => Cas9ParameterPack
    case 3 => Cas9NGGParameterPack
    case 4 => Cas9NAGParameterPack
    case _ => throw new IllegalStateException("Unable to find the correct parameter pack for enzyme: " + index)
  }

  def parameterPackToIndex(pack: ParameterPack): Int = pack match {
    case Cpf1ParameterPack => 1
    case Cas9ParameterPack => 2
    case Cas9NGGParameterPack => 3
    case Cas9NAGParameterPack => 4
    case _ => throw new IllegalStateException("Unable to find the correct parameter pack for enzyme: " + pack.toString)

  }
}


case object Cas9ParameterPack extends ParameterPack {
  def enzyme = SpCAS9
  def pam = Array[String]("GG","AG")

  def totalScanLength: Int = 23
  def comparisonBitEncoding: Long = 0x3FFFFFFFFFC0l // be super careful with this value!! the cas9 mask only considers the lower 46 bites (23 of 24 bases are used)
  // and doesn't care about the NGG or NAG (3 prime) -- it's assumed all OT have the NGG or NAG
  def fivePrimePam: Boolean = false

  override def fwdRegex: Regex = """([ACGTacgt])(?=([ACGTacgt]{20}[AG]G))""".r

  override def revRegex: Regex = """([C])(?=([CT][ACGTacgt]{21}))""".r
}


case object Cas9NGGParameterPack extends ParameterPack {
  def enzyme = SpCAS9
  def pam = Array[String]("GG")

  def totalScanLength: Int = 23
  def comparisonBitEncoding: Long = 0x3FFFFFFFFFC0l // be super careful with this value!! the cas9 mask only considers the lower 46 bites (23 of 24 bases are used)
  // and doesn't care about the NGG or NAG (3 prime) -- it's assumed all OT have the NGG
  def fivePrimePam: Boolean = false

  override def fwdRegex: Regex = """([ACGTacgt])(?=([ACGTacgt]{20}GG))""".r

  override def revRegex: Regex = """([C])(?=(C[ACGTacgt]{21}))""".r
}


case object Cas9NAGParameterPack extends ParameterPack {
  def enzyme = SpCAS9
  def pam = Array[String]("AG")

  def totalScanLength: Int = 23
  def comparisonBitEncoding: Long = 0x3FFFFFFFFFC0l // be super careful with this value!! the cas9 mask only considers the lower 46 bites (23 of 24 bases are used)
  // and doesn't care about the NGG or NAG (3 prime) -- it's assumed all OT have the NAG
  def fivePrimePam: Boolean = false

  override def fwdRegex: Regex = """([ACGTacgt])(?=([ACGTacgt]{20}AG))""".r

  override def revRegex: Regex = """([C])(?=(T[ACGTacgt]{21}))""".r
}

case object Cpf1ParameterPack extends ParameterPack {
  def enzyme = CPF1
  def pam = Array[String]("TTT")
  def totalScanLength: Int = 24
  def comparisonBitEncoding: Long = 0x00FFFFFFFFFFl // be super careful with this value!!
  def fivePrimePam: Boolean = true


  override def fwdRegex: Regex = """(T)(?=(TT[ACGTacgt]{21}))""".r

  override def revRegex: Regex = """([ACGTacgt])(?=([ACGTacgt]{20}AAA))""".r

}

