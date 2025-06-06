package com.gicsports.it.sync.grpc

import com.typesafe.config.{Config, ConfigFactory}
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.NodeConfigs.Miners
import com.gicsports.it.api.SyncGrpcApi.*
import com.gicsports.it.sync.*
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.protobuf.transaction.PBTransactions
import com.gicsports.transaction.assets.IssueTransaction.{MaxAssetDescriptionLength, MaxAssetNameLength, MinAssetNameLength}
import com.gicsports.transaction.smart.script.ScriptCompiler
import io.grpc.Status.Code
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.concurrent.duration.*

class UpdateAssetInfoTransactionGrpcSuite extends GrpcBaseTransactionSuite with TableDrivenPropertyChecks {
  import UpdateAssetInfoTransactionGrpcSuite.*
  val updateInterval                              = 5
  override protected def nodeConfigs: Seq[Config] = Seq(configWithUpdateIntervalSetting(updateInterval).withFallback(Miners.head))

  val issuer      = firstAcc
  val nonIssuer   = secondAcc
  var assetId     = ""
  var issueHeight = 0

  protected override def beforeAll(): Unit = {
    super.beforeAll()

    assetId = PBTransactions
      .vanilla(
        sender.broadcastIssue(
          issuer,
          "asset",
          someAssetAmount,
          8,
          reissuable = true,
          script = Right(None),
          fee = issueFee,
          description = "description",
          version = 1,
          waitForTx = true
        ),
        unsafe = false
      )
      .explicitGet()
      .id()
      .toString
    issueHeight = sender.height
  }

  test("able to update name/description of issued asset") {
    val nextTerm = issueHeight + updateInterval + 1
    sender.waitForHeight(nextTerm, 5.minutes)
    val updateAssetInfoTxId =
      PBTransactions
        .vanilla(
          sender.updateAssetInfo(issuer, assetId, "updatedName", "updatedDescription", issueFee),
          unsafe = false
        )
        .explicitGet()
        .id()
        .toString
    sender.waitForTransaction(updateAssetInfoTxId)

    sender.assetInfo(assetId).name shouldBe "updatedName"
    sender.assetInfo(assetId).description shouldBe "updatedDescription"
  }

  test("not able to update name/description more than once within interval") {
    assertGrpcError(
      sender.updateAssetInfo(issuer, assetId, "updatedName", "updatedDescription", issueFee),
      s"Can't update info of asset with id=$assetId",
      Code.INVALID_ARGUMENT
    )
    sender.waitForHeight(sender.height + updateInterval / 2, 5.minutes)

    assertGrpcError(
      sender.updateAssetInfo(issuer, assetId, "updatedName", "updatedDescription", issueFee),
      s"Can't update info of asset with id=$assetId",
      Code.INVALID_ARGUMENT
    )
  }

  test("can update asset only with name which have valid length") {
    val invalidNames = Seq(
      "",
      "a" * (MinAssetNameLength - 1),
      "a" * (MaxAssetNameLength + 1),
      "~!|#$%^&*()_+=\";:/?><|\\][{}"
    )
    val validNames = Seq("a" * MinAssetNameLength, "a" * MaxAssetNameLength)
    invalidNames.foreach { name =>
      assertGrpcError(
        sender.updateAssetInfo(issuer, assetId, name, "updatedDescription", issueFee),
        "invalid name",
        Code.INVALID_ARGUMENT
      )
    }
    validNames.foreach { name =>
      sender.waitForHeight(sender.height + updateInterval + 1, 10.minutes)
      val tx = sender.updateAssetInfo(issuer, assetId, name, "updatedDescription", issueFee)

      nodes.foreach(_.waitForTxAndHeightArise(tx.id))
      nodes.foreach(_.assetInfo(assetId).name shouldBe name)
    }
  }

  test("can update asset only with description which have valid length") {
    val invalidDescs = Seq("a" * (MaxAssetDescriptionLength + 1))
    val validDescs = Seq("", "a" * MaxAssetDescriptionLength)
    invalidDescs.foreach { desc =>
      assertGrpcError(
        sender.updateAssetInfo(issuer, assetId, "updatedName", desc, issueFee),
        "Too big sequence requested",
        Code.INVALID_ARGUMENT
      )
    }
    validDescs.foreach { desc =>
      sender.waitForHeight(sender.height + updateInterval + 1, 3.minutes)
      val tx = sender.updateAssetInfo(issuer, assetId, "updatedName", desc, issueFee)

      nodes.foreach(_.waitForTxAndHeightArise(tx.id))
      nodes.foreach(_.assetInfo(assetId).description shouldBe desc)
    }
  }

  test("not able to update asset info without paying enough fee") {
    assertGrpcError(
      sender.updateAssetInfo(issuer, assetId, "updatedName", "updatedDescription", issueFee - 1),
      s"does not exceed minimal value of $issueFee GIC",
      Code.INVALID_ARGUMENT
    )
  }

  test("not able to update info of not-issued asset") {
    val notIssuedAssetId = "BzARFPgBqWFu6MHGxwkPVKmaYAzyShu495Ehsgru72Wz"
    assertGrpcError(
      sender.updateAssetInfo(issuer, notIssuedAssetId, "updatedName", "updatedDescription", issueFee),
      "Referenced assetId not found",
      Code.INVALID_ARGUMENT
    )
  }

  test("non-issuer cannot update asset info") {
    assertGrpcError(
      sender.updateAssetInfo(nonIssuer, assetId, "updatedName", "updatedDescription", issueFee),
      "Asset was issued by other address",
      Code.INVALID_ARGUMENT
    )
  }

  test("check increased fee for smart sender/asset") {
    val scriptText = s"""true""".stripMargin
    val script     = ScriptCompiler(scriptText, isAssetScript = true, ScriptEstimatorV2).explicitGet()._1
    val smartAssetId =
      PBTransactions
        .vanilla(
          sender.broadcastIssue(
            issuer,
            "smartAsset",
            someAssetAmount,
            8,
            reissuable = true,
            description = "description",
            fee = issueFee,
            script = Right(Some(script)),
            waitForTx = true
          ),
          unsafe = false
        )
        .explicitGet()
        .id()
        .toString
    sender.waitForHeight(sender.height + updateInterval + 1, 6.minutes)
    assertGrpcError(
      sender.updateAssetInfo(issuer, smartAssetId, "updatedName", "updatedDescription", issueFee + smartFee - 1),
      s"State check failed. Reason: Transaction involves 1 scripted assets. Requires $smartFee extra fee.",
      Code.INVALID_ARGUMENT
    )
    sender.setScript(issuer, Right(Some(script)), fee = setScriptFee, waitForTx = true)
    assertGrpcError(
      sender.updateAssetInfo(issuer, smartAssetId, "updatedName", "updatedDescription", issueFee + 2 * smartFee - 1),
      s"State check failed. Reason: Transaction sent from smart account. Requires $smartFee extra fee.",
      Code.INVALID_ARGUMENT
    )

    sender.updateAssetInfo(issuer, smartAssetId, "updatedName", "updatedDescription", issueFee + 2 * smartFee, waitForTx = true)
  }

}

object UpdateAssetInfoTransactionGrpcSuite {
  private def configWithUpdateIntervalSetting(interval: Long) =
    ConfigFactory.parseString(
      s"""GIC {
         |  blockchain.custom.functionality.min-asset-info-update-interval = $interval
         |  miner.quorum = 0
         |}""".stripMargin
    )
}
