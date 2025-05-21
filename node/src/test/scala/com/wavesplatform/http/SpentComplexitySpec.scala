package com.gicsports.http

import com.gicsports.api.http.{ApiMarshallers, RouteTimeout, TransactionsApiRoute}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.history.Domain
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.transaction.Asset
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.assets.IssueTransaction
import com.gicsports.transaction.smart.{InvokeScriptTransaction, SetScriptTransaction}
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.utils.Signed
import com.gicsports.utils.Schedulers
import com.gicsports.{BlockGen, TestWallet}
import org.scalamock.scalatest.MockFactory
import org.scalatest.OptionValues
import play.api.libs.json.JsObject

import scala.concurrent.duration.*

class SpentComplexitySpec
    extends RouteSpec("/transactions")
    with RestAPISettingsHelper
    with MockFactory
    with BlockGen
    with OptionValues
    with TestWallet
    with WithDomain
    with ApiMarshallers {
  private val contract = TestCompiler(V5)
    .compileContract("""{-# STDLIB_VERSION 5 #-}
                       |{-# CONTENT_TYPE DAPP #-}
                       |{-# SCRIPT_TYPE ACCOUNT #-}
                       |
                       |@Verifier(tx)
                       |func verify() = {
                       |  let i1 = if (sigVerify(tx.bodyBytes, tx.proofs[1], tx.senderPublicKey)) then 1 else 0
                       |  let i2 = if (sigVerify(tx.bodyBytes, tx.proofs[2], tx.senderPublicKey)) then 1 else 0
                       |  let i3 = if (sigVerify(tx.bodyBytes, tx.proofs[3], tx.senderPublicKey)) then 1 else 0
                       |  i1 + i2 + i3 < 10
                       |}
                       |
                       |@Callable(i)
                       |func default() = {
                       |  [StringEntry("a", "b")]
                       |}
                       |""".stripMargin)

  private val assetScript = TestCompiler(V5)
    .compileAsset("""{-# STDLIB_VERSION 5 #-}
                    |{-# CONTENT_TYPE EXPRESSION #-}
                    |{-# SCRIPT_TYPE ASSET #-}
                    |
                    |let i1 = if (sigVerify(tx.bodyBytes, tx.bodyBytes, tx.senderPublicKey)) then 1 else 0
                    |let i2 = if (sigVerify(tx.bodyBytes, tx.bodyBytes, tx.senderPublicKey)) then 1 else 0
                    |
                    |i1 + i2 < 10
                    |""".stripMargin)

  private val settings = DomainPresets.RideV5

  private val sender = testWallet.generateNewAccount().get

  private def route(d: Domain) =
    seal(
      TransactionsApiRoute(
        restAPISettings,
        d.transactionsApi,
        testWallet,
        d.blockchain,
        () => 0,
        DummyTransactionPublisher.accepting,
        ntpTime,
        new RouteTimeout(60.seconds)(Schedulers.fixedPool(1, "heavy-request-scheduler"))
      ).route
    )

  "Invocation" - {
    "does not count verifier complexity when InvokeScript is sent from smart account" in
      withDomain(settings, Seq(AddrWithBalance(sender.toAddress, 10_000_00000000L))) { d =>
        val invokeTx = Signed
          .invokeScript(2.toByte, sender, sender.toAddress, None, Seq.empty, 1_000_0000L, Asset.Waves, ntpTime.getTimestamp())

        d.appendBlock(
          SetScriptTransaction.selfSigned(2.toByte, sender, Some(contract), 1_0000_0000L, ntpTime.getTimestamp()).explicitGet(),
          invokeTx
        )

        Get(s"/transactions/info/${invokeTx.id()}") ~> route(d) ~> check {
          (responseAs[JsObject] \ "spentComplexity").as[Long] shouldBe 2
        }
      }

    "counts asset script complexity when smart asset payment is attached" in {
      val recipient = testWallet.generateNewAccount().get

      withDomain(settings, Seq(AddrWithBalance(sender.toAddress, 10_000_00000000L), AddrWithBalance(recipient.toAddress, 10_00000000L))) { d =>
        val issue = IssueTransaction
          .selfSigned(2.toByte, sender, "TEST", "", 1000_00L, 2.toByte, false, Some(assetScript), 1000_0000_0000L, ntpTime.getTimestamp())
          .explicitGet()

        val transferAsset = TransferTransaction
          .selfSigned(2.toByte, sender, recipient.toAddress, issue.asset, 50_00L, Waves, 600_0000L, ByteStr.empty, ntpTime.getTimestamp())
          .explicitGet()


        val invokeTx = Signed
          .invokeScript(
            2.toByte,
            recipient,
            sender.toAddress,
            None,
            Seq(InvokeScriptTransaction.Payment(50_00L, issue.asset)),
            600_0000L,
            Asset.Waves,
            ntpTime.getTimestamp()
          )

        d.appendBlock(
          issue,
          transferAsset,
          SetScriptTransaction.selfSigned(2.toByte, sender, Some(contract), 1_0000_0000L, ntpTime.getTimestamp()).explicitGet(),
          invokeTx
        )

        Get(s"/transactions/info/${invokeTx.id()}") ~> route(d) ~> check {
          (responseAs[JsObject] \ "spentComplexity").as[Long] shouldBe 2
        }
      }
    }
  }

  "Does not count smart asset complexity for transfer" in {
    val recipient = testWallet.generateNewAccount().get

    withDomain(settings, Seq(AddrWithBalance(sender.toAddress, 10_000_00000000L), AddrWithBalance(recipient.toAddress, 10_00000000L))) { d =>
      val issue = IssueTransaction
        .selfSigned(2.toByte, sender, "TEST", "", 1000_00L, 2.toByte, false, Some(assetScript), 1000_00000000L, ntpTime.getTimestamp())
        .explicitGet()

      val transferAsset = TransferTransaction
        .selfSigned(2.toByte, sender, recipient.toAddress, issue.asset, 50_00L, Waves, 600_0000L, ByteStr.empty, ntpTime.getTimestamp())
        .explicitGet()

      val returnFrom = TransferTransaction
        .selfSigned(2.toByte, recipient, sender.toAddress, issue.asset, 50_00L, Waves, 1000_0000L, ByteStr.empty, ntpTime.getTimestamp())
        .explicitGet()

      d.appendBlock(
        issue,
        transferAsset,
        returnFrom
      )

      val currentRoute = route(d)

      Get(routePath(s"/info/${transferAsset.id()}")) ~> currentRoute ~> check {
        (responseAs[JsObject] \ "spentComplexity").as[Long] shouldBe 0
      }
    }
  }
}
