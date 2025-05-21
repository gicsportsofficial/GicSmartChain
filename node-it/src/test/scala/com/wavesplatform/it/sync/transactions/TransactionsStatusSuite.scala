package com.gicsports.it.sync.transactions

import com.gicsports.api.http.ApiError.InvalidIds
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.NTPTime
import com.gicsports.it.api.SyncHttpApi.*
import com.gicsports.it.api.{TransactionInfo, TransactionStatus}
import com.gicsports.it.sync.*
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{ProvenTransaction, Transaction}
import play.api.libs.json.*

import scala.util.Random

class TransactionsStatusSuite extends BaseTransactionSuite with NTPTime {

  import TransactionsStatusSuite.*

  test("/transactions/status should return correct data") {

    val txs = mkTransactions

    val confirmedTxs   = txs.slice(0, 10)
    val unconfirmedTxs = txs.slice(10, 15)
    val notFoundTxs    = txs.slice(15, 20)
    val txIds          = txs.map(_.id().toString)

    confirmedTxs.foreach(tx => notMiner.postJson("/transactions/broadcast", tx.json()))

    val confirmedTxsInfo = waitForTransactions(confirmedTxs)

    nodes.waitForHeightArise()

    docker.stopContainer(dockerNodes().head)

    unconfirmedTxs.foreach(tx => notMiner.postJson("/transactions/broadcast", tx.json()))

    notMiner.utxSize shouldBe 5

    val checkData = CheckData(notMiner.height, confirmedTxsInfo, unconfirmedTxs.map(_.id().toString), notFoundTxs.map(_.id().toString))

    val postJsonResult = notMiner.transactionStatus(txIds)
    val postFormResult =
      Json.parse(notMiner.postForm("/transactions/status", txIds.map(("id", _))*).getResponseBody).as[List[TransactionStatus]]
    val getResult =
      Json.parse(notMiner.get(s"/transactions/status?${txIds.map(id => s"id=$id").mkString("&")}").getResponseBody).as[List[TransactionStatus]]

    check(checkData, postJsonResult)
    check(checkData, postFormResult)
    check(checkData, getResult)

    val maxTxList = (1 to 1000).map(_ => txIds.head).toList
    val result    = notMiner.transactionStatus(maxTxList)
    result.size shouldBe maxTxList.size
    assert(result.forall(_ == result.head))

    assertBadRequestAndMessage(notMiner.transactionStatus(maxTxList :+ txIds.head), "Too big sequence requested")
    assertBadRequestAndMessage(notMiner.transactionStatus(Seq()), "Empty request")

    assertApiError(notMiner.transactionStatus(Random.shuffle(txIds :+ "illegal id")), InvalidIds(Seq("illegal id")))
  }

  private def check(data: CheckData, result: Seq[TransactionStatus]): Unit = {
    result.size shouldBe data.size

    val confirmed   = result.filter(_.status == "confirmed")
    val unconfirmed = result.filter(_.status == "unconfirmed")
    val notFound    = result.filter(_.status == "not_found")

    confirmed should contain theSameElementsAs data.confirmed
    unconfirmed should contain theSameElementsAs data.unconfirmed
    notFound should contain theSameElementsAs data.notFound
  }

  private def mkTransactions: List[Transaction & ProvenTransaction] =
    (1001 to 1020).map { amount =>
      TransferTransaction
        .selfSigned(
          2.toByte,
          miner.keyPair,
          secondKeyPair.toAddress,
          Waves,
          amount,
          Waves,
          minFee,
          ByteStr.empty,
          ntpTime.correctedTime()
        )
        .explicitGet()
    }.toList

  private def waitForTransactions(txs: List[Transaction]): List[TransactionInfo] =
    txs.map(tx => nodes.waitForTransaction(tx.id().toString))
}

object TransactionsStatusSuite {
  case class CheckData(
      confirmed: List[TransactionStatus],
      unconfirmed: List[TransactionStatus],
      notFound: List[TransactionStatus]
  ) {
    val size: Int = confirmed.size + unconfirmed.size + notFound.size
  }

  object CheckData {
    def apply(height: Int, confirmed: List[TransactionInfo], unconfirmed: List[String], notFound: List[String]): CheckData =
      new CheckData(
        confirmed.map(info => TransactionStatus(info.id, "confirmed", Some(height - info.height), Some(info.height), Some("succeeded"))),
        unconfirmed.map(d => TransactionStatus(d, "unconfirmed", None, None, None)),
        notFound.map(d => TransactionStatus(d, "not_found", None, None, None))
      )
  }
}
