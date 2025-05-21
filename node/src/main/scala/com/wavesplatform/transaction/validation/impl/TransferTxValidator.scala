package com.gicsports.transaction.validation.impl

import cats.data.ValidatedNel
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.validation.TxValidator

object TransferTxValidator extends TxValidator[TransferTransaction] {
  override def validate(transaction: TransferTransaction): ValidatedNel[ValidationError, TransferTransaction] = {
    import transaction._
    V.seq(transaction)(
      V.transferAttachment(attachment),
      V.addressChainId(recipient, chainId)
    )
  }
}
