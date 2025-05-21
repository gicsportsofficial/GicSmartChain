package com.gicsports.transaction.validation.impl

import com.gicsports.transaction.assets.ReissueTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object ReissueTxValidator extends TxValidator[ReissueTransaction] {
  override def validate(tx: ReissueTransaction): ValidatedV[ReissueTransaction] = V.seq(tx)()
}
