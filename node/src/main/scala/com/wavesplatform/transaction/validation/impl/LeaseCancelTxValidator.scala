package com.gicsports.transaction.validation.impl

import cats.data.ValidatedNel
import cats.syntax.either._
import com.gicsports.common.state.ByteStr
import com.gicsports.crypto
import com.gicsports.lang.ValidationError
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.lease.LeaseCancelTransaction
import com.gicsports.transaction.validation.TxValidator

object LeaseCancelTxValidator extends TxValidator[LeaseCancelTransaction] {
  override def validate(tx: LeaseCancelTransaction): ValidatedNel[ValidationError, LeaseCancelTransaction] = {
    import tx._
    V.seq(tx)(
      checkLeaseId(leaseId).toValidatedNel
    )
  }

  def checkLeaseId(leaseId: ByteStr): Either[GenericError, Unit] =
    Either.cond(
      leaseId.arr.length == crypto.DigestLength,
      (),
      GenericError(s"Lease id=$leaseId has invalid length = ${leaseId.arr.length} byte(s) while expecting ${crypto.DigestLength}")
    )
}
