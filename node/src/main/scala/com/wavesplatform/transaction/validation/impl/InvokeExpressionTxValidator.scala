package com.gicsports.transaction.validation.impl

import cats.syntax.either.*
import com.gicsports.lang.v1.ContractLimits
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.smart.InvokeExpressionTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object InvokeExpressionTxValidator extends TxValidator[InvokeExpressionTransaction] {
  override def validate(tx: InvokeExpressionTransaction): ValidatedV[InvokeExpressionTransaction] = {
    val size  = tx.expressionBytes.size
    val limit = ContractLimits.MaxContractSizeInBytes
    V.seq(tx)(
      Either
        .cond(
          size <= limit,
          (),
          GenericError(s"InvokeExpressionTransaction bytes length = $size exceeds limit = $limit")
        )
        .toValidatedNel
    )
  }
}
