package com.gicsports.state

import com.gicsports.common.state.ByteStr
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.TxHelpers

class CommonSpec extends FreeSpec with WithDomain {

  "Common Conditions" - {
    "Zero balance of absent asset" in {
      val sender         = TxHelpers.signer(1)
      val initialBalance = 1000
      val assetId        = Array.fill(32)(1.toByte)

      withDomain(balances = Seq(AddrWithBalance(sender.toAddress, initialBalance))) { d =>
        d.balance(sender.toAddress, IssuedAsset(ByteStr(assetId))) shouldEqual 0L
      }
    }
  }
}
