package com.gicsports.block

import java.nio.ByteBuffer

import com.google.common.primitives.{Bytes, Ints, Longs}
import com.gicsports.block.Block.{PlainBlockVersion, ProtoBlockVersion}
import com.gicsports.common.state.ByteStr
import com.gicsports.protobuf.transaction.PBTransactions
import com.gicsports.protobuf.utils.PBUtils
import com.gicsports.serialization.ByteBufferOps
import com.gicsports.transaction.{EthereumTransaction, Transaction, TransactionParsers}

package object serialization {
  private[block] def writeTransactionData(version: Byte, txs: Seq[Transaction]): Array[Byte] = {
    val txsBytes     = txs.map(tx => if (version == ProtoBlockVersion) PBUtils.encodeDeterministic(PBTransactions.protobuf(tx)) else tx.bytes().ensuring(!tx.isInstanceOf[EthereumTransaction]))
    val txsBytesSize = txsBytes.map(_.length + Ints.BYTES).sum
    val txsBuf       = ByteBuffer.allocate(txsBytesSize)
    txsBytes.foreach(tx => txsBuf.putInt(tx.length).put(tx))

    Bytes.concat(mkTxsCountBytes(version, txs.size), txsBuf.array())
  }

  private[block] def readTransactionData(version: Byte, buf: ByteBuffer): Seq[Transaction] =
    Seq.fill(
      if (version <= PlainBlockVersion) buf.getByte
      else if (version <= ProtoBlockVersion) buf.getInt
      else throw new IllegalArgumentException(s"Unexpected block version: $version")
    )(TransactionParsers.parseBytes(buf.getByteArray(buf.getInt)).get)

  private[block] def writeConsensusBytes(baseTarget: Long, generationSignature: ByteStr): Array[Byte] =
    Bytes.concat(
      Longs.toByteArray(baseTarget),
      generationSignature.arr
    )

  def mkTxsCountBytes(version: Byte, txsCount: Int): Array[Byte] =
    if (version <= PlainBlockVersion) Array(txsCount.toByte)
    else if (version <= ProtoBlockVersion) Ints.toByteArray(txsCount)
    else throw new IllegalArgumentException(s"Unexpected block version: $version")

}
