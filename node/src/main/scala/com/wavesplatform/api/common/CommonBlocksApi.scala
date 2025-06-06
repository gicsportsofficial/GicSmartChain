package com.gicsports.api.common

import com.gicsports.account.Address
import com.gicsports.api.BlockMeta
import com.gicsports.block.Block.BlockId
import com.gicsports.common.state.ByteStr
import com.gicsports.state.{Blockchain, TxMeta}
import com.gicsports.transaction.Transaction
import monix.reactive.Observable

trait CommonBlocksApi {
  def blockDelay(blockId: BlockId, blockNum: Int): Option[Long]

  def currentHeight: Int

  def block(blockId: BlockId): Option[(BlockMeta, Seq[(TxMeta, Transaction)])]

  def blockAtHeight(height: Int): Option[(BlockMeta, Seq[(TxMeta, Transaction)])]

  def blocksRange(fromHeight: Int, toHeight: Int): Observable[(BlockMeta, Seq[(TxMeta, Transaction)])]

  def blocksRange(fromHeight: Int, toHeight: Int, generatorAddress: Address): Observable[(BlockMeta, Seq[(TxMeta, Transaction)])]

  def meta(id: ByteStr): Option[BlockMeta]

  def metaAtHeight(height: Int): Option[BlockMeta]

  def metaRange(fromHeight: Int, toHeight: Int): Observable[BlockMeta]
}

object CommonBlocksApi {
  def apply(
      blockchain: Blockchain,
      metaAt: Int => Option[BlockMeta],
      blockInfoAt: Int => Option[(BlockMeta, Seq[(TxMeta, Transaction)])]
  ): CommonBlocksApi = new CommonBlocksApi {
    private def fixHeight(h: Int) = if (h <= 0) blockchain.height + h else h

    def blocksRange(fromHeight: Int, toHeight: Int): Observable[(BlockMeta, Seq[(TxMeta, Transaction)])] =
      Observable
        .fromIterable(fixHeight(fromHeight) to fixHeight(toHeight))
        .map(blockInfoAt)
        .takeWhile(_.isDefined)
        .flatMap(Observable.fromIterable(_))

    def blocksRange(fromHeight: Int, toHeight: Int, generatorAddress: Address): Observable[(BlockMeta, Seq[(TxMeta, Transaction)])] =
      Observable.fromIterable(
        (fixHeight(fromHeight) to fixHeight(toHeight))
          .flatMap(h => metaAt(h))
          .collect { case m if m.header.generator.toAddress == generatorAddress => m.height }
          .flatMap(h => blockInfoAt(h))
      )

    def blockDelay(blockId: BlockId, blockNum: Int): Option[Long] =
      blockchain
        .heightOf(blockId)
        .map { maxHeight =>
          val minHeight  = maxHeight - blockNum.max(1)
          val allHeaders = (minHeight to maxHeight).flatMap(h => metaAt(h))
          val totalPeriod = allHeaders
            .sliding(2)
            .map { pair =>
              pair(1).header.timestamp - pair(0).header.timestamp
            }
            .sum
          totalPeriod / (allHeaders.size - 1).max(1)
        }

    def currentHeight: Int = blockchain.height

    def blockAtHeight(height: Int): Option[(BlockMeta, Seq[(TxMeta, Transaction)])] = blockInfoAt(height)

    def metaAtHeight(height: Int): Option[BlockMeta] = metaAt(height)

    def meta(id: ByteStr): Option[BlockMeta] = blockchain.heightOf(id).flatMap(metaAt)

    def metaRange(fromHeight: Int, toHeight: Int): Observable[BlockMeta] =
      Observable.fromIterable((fixHeight(fromHeight) to fixHeight(toHeight)).flatMap(h => metaAt(h)))

    def block(blockId: BlockId): Option[(BlockMeta, Seq[(TxMeta, Transaction)])] = blockchain.heightOf(blockId).flatMap(h => blockInfoAt(h))
  }
}
