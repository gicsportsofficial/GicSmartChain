package com.gicsports.transaction.smart
import com.gicsports.lang.v1.FunctionHeader.User
import com.gicsports.lang.v1.compiler.Terms.FUNCTION_CALL
import com.gicsports.lang.v1.evaluator.ContractEvaluator
import com.gicsports.state.diffs.invoke.InvokeScriptTransactionLike
import com.gicsports.transaction.{Asset, FastHashId, ProvenTransaction, Transaction, TxWithFee, VersionedTransaction}

trait InvokeTransaction
    extends Transaction
    with InvokeScriptTransactionLike
    with ProvenTransaction
    with TxWithFee.InCustomAsset
    with FastHashId
    with VersionedTransaction {
  override val checkedAssets: Seq[Asset.IssuedAsset] = super[InvokeScriptTransactionLike].checkedAssets
}

object InvokeTransaction {
  val DefaultCall: FUNCTION_CALL = FUNCTION_CALL(User(ContractEvaluator.DEFAULT_FUNC_NAME), Nil)
}
