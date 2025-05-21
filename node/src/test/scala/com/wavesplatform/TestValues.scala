package com.gicsports

import com.gicsports.account.{Address, KeyPair}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.v1.estimator.ScriptEstimatorV1
import com.gicsports.state.{AssetDescription, Height}
import com.gicsports.state.diffs.FeeValidation.{FeeConstants, FeeUnit, ScriptExtraFee}
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.{TransactionType, TxHelpers}
import com.gicsports.transaction.smart.script.ScriptCompiler

object TestValues {
  val keyPair: KeyPair   = TxHelpers.defaultSigner
  val address: Address   = keyPair.toAddress
  val asset: IssuedAsset = IssuedAsset(ByteStr(("A" * 32).getBytes("ASCII")))
  val bigMoney: Long     = com.gicsports.state.diffs.ENOUGH_AMT
  val timestamp: Long    = System.currentTimeMillis()
  val fee: Long          = 1e8.toLong
  val feeMiddle: Long    = 4e6.toLong
  val feeSmall: Long     = 2e6.toLong

  val invokeFee: Long = FeeUnit * FeeConstants(TransactionType.InvokeScript)

  def invokeFee(scripts: Int = 0, issues: Int = 0): Long =
    invokeFee + scripts * ScriptExtraFee + issues * FeeConstants(TransactionType.Issue) * FeeUnit

  val (script, scriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |true
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()

  val (assetScript, assetScriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ASSET #-}
      |true
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()

  val (rejectAssetScript, rejectAssetScriptComplexity) = ScriptCompiler
    .compile(
      """
      |{-# STDLIB_VERSION 2 #-}
      |{-# CONTENT_TYPE EXPRESSION #-}
      |{-# SCRIPT_TYPE ASSET #-}
      |false
      |""".stripMargin,
      ScriptEstimatorV1
    )
    .explicitGet()

  val assetDescription: AssetDescription = AssetDescription(
    asset.id,
    TxHelpers.defaultSigner.publicKey,
    null,
    null,
    0,
    reissuable = true,
    BigInt(1),
    Height(1),
    None,
    0,
    nft = false
  )
}
