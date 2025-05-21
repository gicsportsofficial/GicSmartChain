package com.gicsports.utils

import com.gicsports.common.state.ByteStr
import com.gicsports.state.Diff
import org.scalatest.matchers.{Matcher, MatchResult}

trait DiffMatchers {
  def containAppliedTx(transactionId: ByteStr) = new DiffAppliedTxMatcher(transactionId, true)
  def containFailedTx(transactionId: ByteStr)  = new DiffAppliedTxMatcher(transactionId, false)

  class DiffAppliedTxMatcher(transactionId: ByteStr, shouldBeApplied: Boolean) extends Matcher[Diff] {
    override def apply(diff: Diff): MatchResult = {
      val isApplied = diff.transaction(transactionId) match {
        case Some(nt) if nt.applied => true
        case _                      => false
      }

      MatchResult(
        shouldBeApplied == isApplied,
        s"$transactionId was not ${if (shouldBeApplied) "applied" else "failed"}: $diff",
        s"$transactionId was ${if (shouldBeApplied) "applied" else "failed"}: $diff"
      )
    }
  }
}
