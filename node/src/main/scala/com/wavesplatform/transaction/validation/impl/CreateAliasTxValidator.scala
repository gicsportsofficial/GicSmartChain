package com.gicsports.transaction.validation.impl

import cats.data.Validated
import com.gicsports.account.Alias
import com.gicsports.transaction.CreateAliasTransaction
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object CreateAliasTxValidator extends TxValidator[CreateAliasTransaction] {
  override def validate(tx: CreateAliasTransaction): ValidatedV[CreateAliasTransaction] = {
    import tx._
    V.seq(tx)(
      Validated.fromEither(Alias.createWithChainId(aliasName, chainId)).toValidatedNel.map((_: Alias) => tx)
    )
  }
}
