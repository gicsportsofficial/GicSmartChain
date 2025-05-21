package com.gicsports.transaction.validation.impl

import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.assets.IssueTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}
import com.gicsports.transaction.TxVersion

object IssueTxValidator extends TxValidator[IssueTransaction] {
  override def validate(tx: IssueTransaction): ValidatedV[IssueTransaction] = {

    import tx._
    V.seq(tx)(
      V.assetName(tx.name),
      V.assetDescription(tx.description),
      V.cond(version > TxVersion.V1 || script.isEmpty, GenericError("Script not supported")),
      V.cond(script.forall(_.isInstanceOf[ExprScript]), GenericError(s"Asset can only be assigned with Expression script, not Contract"))
    )
  }
}
