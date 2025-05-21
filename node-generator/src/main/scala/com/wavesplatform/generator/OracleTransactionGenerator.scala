package com.gicsports.generator

import cats.Show
import com.gicsports.account.KeyPair
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.generator.OracleTransactionGenerator.Settings
import com.gicsports.generator.utils.Gen
import com.gicsports.generator.utils.Implicits.DoubleExt
import com.gicsports.lang.v1.estimator.ScriptEstimator
import com.gicsports.state._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{DataTransaction, Transaction}

class OracleTransactionGenerator(settings: Settings, val accounts: Seq[KeyPair], estimator: ScriptEstimator) extends TransactionGenerator {
  override def next(): Iterator[Transaction] = generate(settings).iterator

  def generate(settings: Settings): Seq[Transaction] = {
    val oracle = accounts.last

    val scriptedAccount = accounts.head

    val script = Gen.oracleScript(oracle, settings.requiredData, estimator)

    val enoughFee = 0.06.waves

    val setScript: Transaction =
      SetScriptTransaction
        .selfSigned(1.toByte, scriptedAccount, Some(script), enoughFee, timestamp = System.currentTimeMillis())
        .explicitGet()

    val setDataTx: Transaction = DataTransaction
      .selfSigned(1.toByte, oracle, settings.requiredData.toList, enoughFee, System.currentTimeMillis())
      .explicitGet()

    val now = System.currentTimeMillis()
    val transactions: List[Transaction] = (1 to settings.transactions).map { i =>
      TransferTransaction
        .selfSigned(2.toByte, scriptedAccount, oracle.toAddress, Waves, 1.waves, Waves, enoughFee, ByteStr.empty, now + i)
        .explicitGet()
    }.toList

    setScript +: setDataTx +: transactions
  }
}

object OracleTransactionGenerator {
  final case class Settings(transactions: Int, requiredData: Set[DataEntry[_]])

  object Settings {
    implicit val toPrintable: Show[Settings] = { x =>
      s"Transactions: ${x.transactions}\n" +
        s"DataEntries: ${x.requiredData}\n"
    }
  }
}
