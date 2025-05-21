package com.gicsports.state.diffs.invoke

import com.gicsports.account.*
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.v1.compiler.Terms.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.smart.InvokeScriptTransaction
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.{Authorized, TransactionBase, TxTimestamp}

trait InvokeScriptLike {
  def dApp: AddressOrAlias
  def funcCall: FUNCTION_CALL
  def payments: Seq[Payment]
  def root: InvokeScriptTransactionLike
  def checkedAssets: Seq[IssuedAsset] = payments collect { case Payment(_, assetId: IssuedAsset) => assetId }
  val sender: PublicKey
}

trait InvokeScriptTransactionLike extends TransactionBase with InvokeScriptLike with Authorized

object InvokeScriptLike {
  implicit class ISLExt(val isl: InvokeScriptLike) extends AnyVal {
    def enableEmptyKeys: Boolean = isl.root match {
      case ist: InvokeScriptTransaction => ist.version == 1
      case _                            => true
    }

    def paymentAssets: Seq[IssuedAsset] = isl.payments.collect(IssuedAssets)

    def txId: ByteStr          = isl.root.id()
    def timestamp: TxTimestamp = isl.root.timestamp
  }

  val IssuedAssets: PartialFunction[Payment, IssuedAsset] = { case Payment(_, assetId: IssuedAsset) => assetId }
}

case class InvokeScript(
    sender: PublicKey,
    dApp: Address,
    funcCall: FUNCTION_CALL,
    payments: Seq[Payment],
    root: InvokeScriptTransactionLike
) extends InvokeScriptLike
