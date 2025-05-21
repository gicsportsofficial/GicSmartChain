package com.gicsports.state.patch

import com.gicsports.account.Address
import com.gicsports.common.utils._
import com.gicsports.state.patch.CancelAllLeases.CancelledLeases
import com.gicsports.state.{Blockchain, Diff, Portfolio}

case object CancelLeaseOverflow extends PatchAtHeight('L' -> 930000) {
  def apply(blockchain: Blockchain): Diff = {
    val patch = readPatchData[CancelledLeases]()
    val pfs = patch.balances.map[Address, Portfolio] {
      case (address, lb) =>
        Address.fromString(address).explicitGet() -> Portfolio(lease = lb)
    }
    Diff(portfolios = pfs, leaseState = patch.leaseStates)
  }
}
