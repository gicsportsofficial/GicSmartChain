package com.gicsports.it.sync.smartcontract.smartasset

import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync.{someAssetAmount, _}
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.transaction.smart.script.ScriptCompiler
import org.scalatest.prop.TableDrivenPropertyChecks

class AssetUnsupportedTransactionsSuite extends BaseTransactionSuite with TableDrivenPropertyChecks {

  forAll(
    Table(
      "tx",
      "SponsorFeeTransaction",
      "LeaseTransaction",
      "LeaseCancelTransaction",
      "CreateAliasTransaction",
      "SetScriptTransaction",
      "DataTransaction",
      "IssueTransaction"
    )
  ) { tx =>
    test(s"Smart Asset script should not support $tx") {
      try {
        sender.issue(
          firstKeyPair,
          "MyAsset",
          "Test Asset",
          someAssetAmount,
          0,
          reissuable = true,
          issueFee,
          2,
          Some(
            ScriptCompiler(
              s"""
                 |match tx {
                 |  case _: $tx => true
                 |  case _ => true
                 |}""".stripMargin,
              isAssetScript = true,
              ScriptEstimatorV2
            ).explicitGet()._1.bytes().base64
          ),
          waitForTx = true
        )

        fail("ScriptCompiler didn't throw expected error")
      } catch {
        case ex: java.lang.Exception => ex.getMessage should include("Matching not exhaustive: possibleTypes are")
        case _: Throwable            => fail("ScriptCompiler works incorrect for orders with smart assets")
      }
    }
  }

  test("cannot sponsor scripted asset") {
    val assetId = sender
      .issue(
        firstKeyPair,
        "MyAsset",
        "Test Asset",
        someAssetAmount,
        0,
        reissuable = true,
        issueFee,
        2,
        Some(scriptBase64),
        waitForTx = true
      )
      .id
    assertBadRequestAndMessage(
      sender.sponsorAsset(firstKeyPair, assetId, 100, sponsorReducedFee + smartFee),
      "State check failed. Reason: Sponsorship smart assets is disabled."
    )

  }

}
