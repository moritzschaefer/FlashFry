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

package reference.traverser.dump

import reference.traverser.Traverser
import java.io._
import java.nio.ByteBuffer
import java.nio.channels.{Channels, FileChannel, SeekableByteChannel}
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import htsjdk.samtools.util.{BlockCompressedFilePointerUtil, BlockCompressedInputStream, BlockGunzipper}

import scala.annotation._
import bitcoding.{BitEncoding, BitPosition}
import com.typesafe.scalalogging.LazyLogging
import crispr.{CRISPRSiteOT, ResultsAggregator}
import reference.binary.blocks.BlockManager
import utils.{BaseCombinationGenerator, Utils}
import reference.binary.{BinaryHeader, BlockOffset}
import standards.ParameterPack

/**
  * Created by aaronmck on 5/9/17.
  */
object DumpAllGuides extends LazyLogging {

  /**
    * scan against the binary database of off-target sites in an implmenetation specific way
    *
    * @param binaryFile    the file we're scanning from
    * @param header        we have to parse the header ahead of time so that we know
    * @param aggregator    guides
    * @param maxMismatch   how many mismatches we support
    * @param configuration our enzyme configuration
    * @param bitCoder      our bit encoder
    * @param posCoder      the position encoder
    * @return a guide to OT hit array
    */
  def toFile(binaryFile: File,
           header: BinaryHeader,
           configuration: ParameterPack,
           bitCoder: BitEncoding,
           posCoder: BitPosition,
           outputFile: String) {

    val outputTargets = new PrintWriter(outputFile)

    val formatter = java.text.NumberFormat.getInstance()

    val blockCompressedInput = new BlockCompressedInputStream(binaryFile)

    // setup our input file
    val filePath = Paths.get(binaryFile.getAbsolutePath)
    val channel = FileChannel.open(filePath, StandardOpenOption.READ)
    val inputStream = Channels.newInputStream(channel)

    val blockManager = new BlockManager(header.binWidth, 4, bitCoder)

    var t0 = System.nanoTime()
    var binIndex = 0

    var totalKeptTargets = 0
    var totalTargets = 0
    // ------------------------------------------ traversal ------------------------------------------
    header.inputBinGenerator.iterator.foreach {
      binString => {

        // get the bin information, fetch that block, and get the underlying data
        assert(header.blockOffsets contains binString)

        val binPositionInformation = header.blockOffsets(binString)

        if (binPositionInformation.blockPosition != 0) // we can't do this before we start moving in the file, if we do the BlockCompressedStream will throw a null pointer exception
          assert(blockCompressedInput.getPosition == binPositionInformation.blockPosition, "Positions don't match up, expecting " + binPositionInformation.blockPosition + " but we're currently at " + blockCompressedInput.getPosition)
        val longBuffer = fillBlock(blockCompressedInput, binPositionInformation, new File(binaryFile.getAbsolutePath), binString, bitCoder)

        val targets = blockManager.decodeToTargets(longBuffer,
          binPositionInformation.numberOfTargets,
          bitCoder,
          bitCoder.binToLongComparitor(binString))

        targets.foreach{target => {
          if (target.coordinates.size == 1) {
            val sequence = bitCoder.bitDecodeString(target.sequence)
            val longestPoly = Utils.longestHomopolymerRun(sequence.str)
            val entropy = Utils.sequenceEntropy(sequence.str)
            if (longestPoly < 5 && entropy >= 1.5 && sequence.count == 1) {
              outputTargets.write(">" + sequence.str + "_" + sequence.count + "_" + longestPoly + "_" + entropy + "\n" + sequence.str + "\n")
              totalKeptTargets += 1
            }
            totalTargets += 1
          }
        }}

        binIndex += 1
        if (binIndex % 1000 == 0) {
          logger.info("dumped bin " + binIndex + " taking " + ((System.nanoTime() - t0) / 1000000000.0) + " seconds/1K bins, executed ")
          t0 = System.nanoTime()
        }

      }
    }
    logger.info("Kept targets " + totalKeptTargets + " of a total " + totalTargets)
    outputTargets.close()

  }

  /**
    * fill a block of off-targets from the database
    *
    * @param blockCompressedInput the block compressed stream to pull from
    * @param blockInformation     information about the block we'd like to fetch
    * @param file                 file name to use
    * @return
    */
  private def fillBlock(blockCompressedInput: BlockCompressedInputStream, blockInformation: BlockOffset, file: File, bin: String, bitCoder: BitEncoding): (Array[Long]) = {

    assert(blockInformation.uncompressedSize >= 0, "Bin sizes must be positive (or zero)")

    val readToBlock = new Array[Byte](blockInformation.uncompressedSize)
    val read = blockCompressedInput.read(readToBlock)

    Utils.byteArrayToLong(readToBlock)
  }


}
