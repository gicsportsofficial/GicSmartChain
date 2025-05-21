package com.gicsports.transaction.validation.impl

import cats.syntax.validated._
import com.gicsports.transaction.assets.BurnTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object BurnTxValidator extends TxValidator[BurnTransaction] {
  override def validate(tx: BurnTransaction): ValidatedV[BurnTransaction] = tx.validNel
}
