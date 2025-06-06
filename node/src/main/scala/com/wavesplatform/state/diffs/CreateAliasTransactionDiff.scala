package com.gicsports.state.diffs

import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.ValidationError
import com.gicsports.state.{Blockchain, Diff, Portfolio}
import com.gicsports.transaction.CreateAliasTransaction
import com.gicsports.transaction.TxValidationError.GenericError

object CreateAliasTransactionDiff {
  def apply(blockchain: Blockchain)(tx: CreateAliasTransaction): Either[ValidationError, Diff] =
    if (blockchain.isFeatureActivated(BlockchainFeatures.DataTransaction, blockchain.height) && !blockchain.canCreateAlias(tx.alias))
      Left(GenericError("Alias already claimed"))
    else if (tx.proofs.size > 1 && !blockchain.isFeatureActivated(BlockchainFeatures.RideV6))
      Left(GenericError("Invalid proofs size"))
    else
      Right(
        Diff(
          portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee.value)),
          aliases = Map(tx.alias -> tx.sender.toAddress),
          scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
        )
      )
}
