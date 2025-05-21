package com.gicsports.generator

import cats.Show
import com.gicsports.account.KeyPair
import com.gicsports.generator.WideTransactionGenerator.Settings
import com.gicsports.generator.utils.Gen
import com.gicsports.transaction.Transaction

class WideTransactionGenerator(settings: Settings, accounts: Seq[KeyPair]) extends TransactionGenerator {
  require(accounts.nonEmpty)

  private val limitedRecipientGen = Gen.address(settings.limitDestAccounts)

  override def next(): Iterator[Transaction] = {
    Gen.txs(settings.minFee, settings.maxFee, accounts, limitedRecipientGen).take(settings.transactions)
  }

}

object WideTransactionGenerator {

  case class Settings(transactions: Int, limitDestAccounts: Option[Int], minFee: Long, maxFee: Long) {
    require(transactions > 0)
    require(limitDestAccounts.forall(_ > 0))
  }

  object Settings {
    implicit val toPrintable: Show[Settings] = { x =>
      import x._
      s"""transactions per iteration: $transactions
         |number of recipients is ${limitDestAccounts.map(x => s"limited by $x").getOrElse("not limited")}
         |min fee: $minFee
         |max fee: $maxFee""".stripMargin
    }
  }

}
