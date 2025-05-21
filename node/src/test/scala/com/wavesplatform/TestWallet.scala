package com.gicsports

import com.google.common.primitives.Longs
import com.gicsports.common.state.ByteStr
import com.gicsports.settings.WalletSettings
import com.gicsports.wallet.Wallet

trait TestWallet {
  protected val testWallet: Wallet = TestWallet.instance
}

object TestWallet {
  private[TestWallet] lazy val instance = Wallet(WalletSettings(None, Some("123"), Some(ByteStr(Longs.toByteArray(System.nanoTime())))))
}
