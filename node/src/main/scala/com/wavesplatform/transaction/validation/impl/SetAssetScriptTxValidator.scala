package com.gicsports.transaction.validation.impl

import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.assets.SetAssetScriptTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object SetAssetScriptTxValidator extends TxValidator[SetAssetScriptTransaction] {
  override def validate(tx: SetAssetScriptTransaction): ValidatedV[SetAssetScriptTransaction] = {
    import tx._
    V.seq(tx)(
      V.cond(
        script.forall(_.isInstanceOf[ExprScript]),
        GenericError(s"Asset can only be assigned with Expression script, not Contract")
      ),
      V.cond(
        script.isDefined,
        GenericError("Cannot set empty script")
      )
    )
  }
}
