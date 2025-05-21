package com.gicsports.transaction.validation.impl

import com.gicsports.account.{Address, Alias}
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.transfer.MassTransferTransaction
import com.gicsports.transaction.transfer.MassTransferTransaction.MaxTransferCount
import com.gicsports.transaction.validation.{TxValidator, ValidatedV}

object MassTransferTxValidator extends TxValidator[MassTransferTransaction] {
  override def validate(tx: MassTransferTransaction): ValidatedV[MassTransferTransaction] = {
    import tx.*
    V.seq(tx)(
      V.noOverflow((fee.value +: transfers.map(_.amount.value))*),
      V.cond(transfers.length <= MaxTransferCount, GenericError(s"Number of transfers ${transfers.length} is greater than $MaxTransferCount")),
      V.transferAttachment(attachment),
      V.chainIds(chainId, transfers.view.map(_.address).collect {
        case wa: Address => wa.chainId
        case wl: Alias        => wl.chainId
      }.toSeq*)
    )
  }
}
