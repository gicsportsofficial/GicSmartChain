package com.gicsports.transaction.validation.impl

import cats.data.ValidatedNel
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.PaymentTransaction
import com.gicsports.transaction.validation.TxValidator

object PaymentTxValidator extends TxValidator[PaymentTransaction] {
  override def validate(transaction: PaymentTransaction): ValidatedNel[ValidationError, PaymentTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.noOverflow(fee.value, amount.value),
      V.addressChainId(recipient, chainId)
    )
  }
}
