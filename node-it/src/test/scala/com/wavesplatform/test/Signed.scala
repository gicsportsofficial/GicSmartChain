package com.gicsports.test

import com.gicsports.account.{AddressOrAlias, KeyPair}
import com.gicsports.common.utils.*
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.transaction.smart.InvokeScriptTransaction
import com.gicsports.transaction.{Asset, Proofs, TxTimestamp}

object Signed {
  def invokeScript(
      version: Byte,
      sender: KeyPair,
      dApp: AddressOrAlias,
      functionCall: Option[Terms.FUNCTION_CALL],
      payments: Seq[InvokeScriptTransaction.Payment],
      fee: Long,
      feeAssetId: Asset,
      timestamp: TxTimestamp
  ): InvokeScriptTransaction =
    InvokeScriptTransaction
      .create(version, sender.publicKey, dApp, functionCall, payments, fee, feeAssetId, timestamp, Proofs.empty, dApp.chainId)
      .map(_.signWith(sender.privateKey))
      .explicitGet()
}
