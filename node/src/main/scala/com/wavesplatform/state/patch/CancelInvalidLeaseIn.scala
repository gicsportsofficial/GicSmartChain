package com.gicsports.state.patch

import com.gicsports.account.Address
import com.gicsports.common.utils.EitherExt2
import com.gicsports.state._

case object CancelInvalidLeaseIn extends PatchAtHeight('W' -> 1060000) {
  def apply(blockchain: Blockchain): Diff = {
    Diff(portfolios = readPatchData[Map[String, LeaseBalance]]().map {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    })
  }
}
