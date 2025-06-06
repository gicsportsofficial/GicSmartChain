package com.gicsports.it.sync.transactions

import com.typesafe.config.Config
import com.gicsports.features.{BlockchainFeatureStatus, BlockchainFeatures}
import com.gicsports.it.NodeConfigs
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync.{issueFee, scriptBase64, setAssetScriptFee, someAssetAmount}
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.common.utils._
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.transaction.smart.script.ScriptCompiler

class SetAssetScriptTxFeatureSuite extends BaseTransactionSuite {

  private val featureActivationHeight = 8

  private var assetId = ""

  override def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .overrideBase(_.raw(s"""GIC {
                             |  blockchain.custom.functionality {
                             |    pre-activated-features = {
                             |      ${BlockchainFeatures.SmartAssets.id} = $featureActivationHeight
                             |    }
                             |    
                             |  }
                             |}""".stripMargin))
      .withDefault(1)
      .withSpecial(_.nonMiner)
      .buildNonConflicting()

  override def beforeAll(): Unit = {
    super.beforeAll()

    assetId = sender
      .issue(
        firstKeyPair,
        "SetAssetScript",
        "Test coin for SetAssetScript tests",
        someAssetAmount,
        0,
        reissuable = false,
        issueFee,
        2,
        Some(scriptBase64)
      )
      .id

    nodes.waitForHeightAriseAndTxPresent(assetId)
  }

  test("cannot transact without activated feature") {
    assertBadRequestAndResponse(
      sender.setAssetScript(assetId, firstKeyPair, setAssetScriptFee, Some(scriptBase64)).id,
      s"${BlockchainFeatures.SmartAssets.description} feature has not been activated yet"
    )
  }

  test("can transact after feature activation") {
    nodes.waitForHeight(featureActivationHeight)

    sender.featureActivationStatus(BlockchainFeatures.SmartAssets.id).blockchainStatus shouldBe BlockchainFeatureStatus.Activated

    val script = ScriptCompiler
      .compile(
        s"""
           |match tx {
           |  case _: SetAssetScriptTransaction => true
           |  case _ => false
           |}""".stripMargin,
        ScriptEstimatorV2
      )
      .explicitGet()
      ._1
      .bytes()
      .base64

    val txId = sender
      .setAssetScript(
        assetId,
        firstKeyPair,
        setAssetScriptFee,
        Some(script)
      )
      .id

    nodes.waitForHeightAriseAndTxPresent(txId)
  }
}
