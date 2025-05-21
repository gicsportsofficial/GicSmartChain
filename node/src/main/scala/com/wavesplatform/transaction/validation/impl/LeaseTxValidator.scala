package com.gicsports.transaction.validation.impl

import cats.data.ValidatedNel
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.lease.LeaseTransaction
import com.gicsports.transaction.validation.TxValidator
import com.gicsports.transaction.TxValidationError

object LeaseTxValidator extends TxValidator[LeaseTransaction] {
  override def validate(tx: LeaseTransaction): ValidatedNel[ValidationError, LeaseTransaction] = {
    import tx._
    V.seq(tx)(
      V.noOverflow(amount.value, fee.value),
      V.cond(sender.toAddress != recipient, TxValidationError.ToSelf),
      V.addressChainId(recipient, chainId)
    )
  }

  def validateAmount(amount: Long) =
    Either.cond(amount > 0, (), TxValidationError.NonPositiveAmount(amount, "GIC"))
}
