package com.gicsports.state.diffs.smart

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lang.directives.values.{Expression, V3}
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils.*
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.assets.{IssueTransaction, SetAssetScriptTransaction}
import com.gicsports.transaction.transfer.*
import com.gicsports.transaction.{GenesisTransaction, TxHelpers}

class SmartAssetEvalTest extends PropSpec with WithState {

  val preconditions: (GenesisTransaction, IssueTransaction, SetAssetScriptTransaction, TransferTransaction) = {
    val firstAcc  = TxHelpers.signer(1)
    val secondAcc = TxHelpers.signer(2)

    val genesis = TxHelpers.genesis(firstAcc.toAddress)

    val emptyScript       = s"""
                     |{-# STDLIB_VERSION 3 #-}
                     |{-# CONTENT_TYPE EXPRESSION #-}
                     |{-# SCRIPT_TYPE ASSET #-}
                     |
                     |true
                     |
        """.stripMargin
    val parsedEmptyScript = Parser.parseExpr(emptyScript).get.value
    val emptyExprScript =
      ExprScript(V3, ExpressionCompiler(compilerContext(V3, Expression, isAssetScript = true), parsedEmptyScript).explicitGet()._1)
        .explicitGet()
    val issue = TxHelpers.issue(firstAcc, 100, script = Some(emptyExprScript), reissuable = false)
    val asset = IssuedAsset(issue.id())

    val assetScript   = s"""
                     | {-# STDLIB_VERSION 3 #-}
                     | {-# CONTENT_TYPE EXPRESSION #-}
                     | {-# SCRIPT_TYPE ASSET #-}
                     |
                     | this.id         == base58'${asset.id.toString}' &&
                     | this.quantity   == 100                        &&
                     | this.decimals   == 0                          &&
                     | this.reissuable == false                      &&
                     | this.scripted   == true                       &&
                     | this.sponsored  == false
                     |
        """.stripMargin
    val untypedScript = Parser.parseExpr(assetScript).get.value
    val typedScript = ExprScript(V3, ExpressionCompiler(compilerContext(V3, Expression, isAssetScript = true), untypedScript).explicitGet()._1)
      .explicitGet()
    val setAssetScript = TxHelpers.setAssetScript(firstAcc, asset, typedScript)

    val assetTransfer = TxHelpers.transfer(firstAcc, secondAcc.toAddress, 1, asset)

    (genesis, issue, setAssetScript, assetTransfer)
  }

  property("Smart asset with scrtipt that contains 'this' link") {
    val (genesis, issueTransaction, setAssetScriptTransaction, assetTransferTransaction) = preconditions
    assertDiffAndState(smartEnabledFS) { append =>
      append(Seq(genesis)).explicitGet()
      append(Seq(issueTransaction)).explicitGet()
      append(Seq(setAssetScriptTransaction)).explicitGet()
      append(Seq(assetTransferTransaction)).explicitGet()
    }
  }
}
