package com.gicsports.state.diffs

import com.gicsports.lang.ValidationError
import com.gicsports.state.{Diff, Portfolio}
import com.gicsports.transaction.GenesisTransaction
import com.gicsports.transaction.TxValidationError.GenericError

import scala.util.{Left, Right}

object GenesisTransactionDiff {
  def apply(height: Int)(tx: GenesisTransaction): Either[ValidationError, Diff] = {
    if (height != 1) Left(GenericError(s"GenesisTransaction cannot appear in non-initial block ($height)"))
    else
      Right(Diff(portfolios = Map(tx.recipient -> Portfolio(balance = tx.amount.value))))
  }
}
