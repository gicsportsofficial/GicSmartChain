package com.gicsports.mining

import com.gicsports.features.BlockchainFeatures
import com.gicsports.state.{Blockchain, Diff}
import com.gicsports.transaction.Transaction

//noinspection ScalaStyle
object TxEstimators {
  trait Fn {
    def apply(blockchain: Blockchain, transaction: Transaction, diff: Diff): Long
    def minEstimate: Long
  }

  case object sizeInBytes extends Fn {
    override def apply(blockchain: Blockchain, tx: Transaction, diff: Diff): Long =
      if (blockchain.isFeatureActivated(BlockchainFeatures.BlockV5, blockchain.height + 1))
        tx.protoSize()
      else tx.bytesSize
    override val minEstimate = 109L
  }

  case object one extends Fn {
    override def apply(blockchain: Blockchain, tx: Transaction, diff: Diff): Long = 1
    override val minEstimate                                                      = 1L
  }

  case object scriptRunNumber extends Fn {
    override def apply(blockchain: Blockchain, tx: Transaction, diff: Diff): Long = diff.scriptsRun
    override val minEstimate                                                      = 0L
  }

  case object scriptsComplexity extends Fn {
    override def apply(blockchain: Blockchain, tx: Transaction, diff: Diff): Long = diff.scriptsComplexity
    override val minEstimate                                                      = 0L
  }
}
