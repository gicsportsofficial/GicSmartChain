package com.gicsports.state.appender

import com.gicsports.block.Block
import com.gicsports.common.utils.*
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.network.{ExtensionBlocks, InvalidBlockStorage, PeerDatabase}
import com.gicsports.test.*
import com.gicsports.transaction.TxHelpers
import com.gicsports.utils.SystemTime
import com.gicsports.utx.UtxPoolImpl
import monix.execution.Scheduler.Implicits.global

class ExtensionAppenderSpec extends FlatSpec with WithDomain {
  "Extension appender" should "drop duplicate transactions from UTX" in
    withDomain(balances = AddrWithBalance.enoughBalances(TxHelpers.defaultSigner)) { d =>
      val utx  = new UtxPoolImpl(SystemTime, d.blockchain, d.settings.utxSettings, d.settings.maxTxErrorLogSize, d.settings.minerSettings.enable)
      val time = TestTime()
      val extensionAppender = ExtensionAppender(d.blockchain, utx, d.posSelector, time, InvalidBlockStorage.NoOp, PeerDatabase.NoOp, global)(null, _)

      val tx     = TxHelpers.transfer()
      val block1 = d.createBlock(Block.PlainBlockVersion, Seq(tx), strictTime = true)
      utx.putIfNew(tx).resultE.explicitGet()
      d.appendBlock(tx)
      utx.all shouldBe Seq(tx)

      time.setTime(block1.header.timestamp)
      extensionAppender(ExtensionBlocks(d.blockchain.score + block1.blockScore(), Seq(block1))).runSyncUnsafe().explicitGet()
      d.blockchain.height shouldBe 2
      utx.all shouldBe Nil
    }

}
