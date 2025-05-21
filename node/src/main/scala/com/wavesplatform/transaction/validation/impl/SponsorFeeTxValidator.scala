package com.gicsports.transaction.validation.impl

import cats.syntax.validated._
import com.gicsports.transaction.TxValidationError.NegativeMinFee
import com.gicsports.transaction.assets.SponsorFeeTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object SponsorFeeTxValidator extends TxValidator[SponsorFeeTransaction] {
  override def validate(tx: SponsorFeeTransaction): ValidatedV[SponsorFeeTransaction] = tx.validNel

  def checkMinSponsoredAssetFee(minSponsoredAssetFee: Option[Long]): Either[NegativeMinFee, Unit] =
    Either.cond(minSponsoredAssetFee.forall(_ > 0), (), NegativeMinFee(minSponsoredAssetFee.get, "asset"))
}
