package com.gicsports.state.diffs

import cats.syntax.either.*
import com.gicsports.lang.ValidationError
import com.gicsports.state.*
import com.gicsports.transaction.DataTransaction
import com.gicsports.transaction.validation.impl.DataTxValidator

object DataTransactionDiff {
  def apply(blockchain: Blockchain)(tx: DataTransaction): Either[ValidationError, Diff] = {
    val sender = tx.sender.toAddress
    for {
      // Validate data size
      _ <- DataTxValidator.payloadSizeValidation(blockchain, tx).toEither.leftMap(_.head)
    } yield Diff(
      portfolios = Map(sender -> Portfolio(-tx.fee.value)),
      accountData = Map(sender -> AccountDataInfo(tx.data.map(item => item.key -> item).toMap)),
      scriptsRun = DiffsCommon.countScriptRuns(blockchain, tx)
    )
  }
}
