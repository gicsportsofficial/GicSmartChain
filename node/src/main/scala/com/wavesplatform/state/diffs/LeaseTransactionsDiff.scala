package com.gicsports.state.diffs

import com.gicsports.lang.ValidationError
import com.gicsports.state.*
import com.gicsports.transaction.lease.*

object LeaseTransactionsDiff {
  def lease(blockchain: Blockchain)(tx: LeaseTransaction): Either[ValidationError, Diff] =
    DiffsCommon
      .processLease(blockchain, tx.amount.value, tx.sender, tx.recipient, tx.fee.value, tx.id(), tx.id())
      .map(_.withScriptRuns(DiffsCommon.countScriptRuns(blockchain, tx)))

  def leaseCancel(blockchain: Blockchain, time: Long)(tx: LeaseCancelTransaction): Either[ValidationError, Diff] =
    DiffsCommon
      .processLeaseCancel(blockchain, tx.sender, tx.fee.value, time, tx.leaseId, tx.id())
      .map(_.withScriptRuns(DiffsCommon.countScriptRuns(blockchain, tx)))
}
