package com.gicsports.it.sync.transactions

import com.gicsports.common.state.ByteStr
import com.gicsports.it.IntegrationSuiteWithThreeAddresses
import com.gicsports.it.sync.calcDataFee
import com.gicsports.state.BinaryDataEntry

import scala.concurrent.duration._

trait OverflowBlock { self: IntegrationSuiteWithThreeAddresses =>
  // Hack to bypass instant micro mining
  def overflowBlock(): Unit = {
    import com.gicsports.it.api.SyncHttpApi._
    val entries = List.tabulate(4)(n => BinaryDataEntry(n.toString, ByteStr(Array.fill(32724)(n.toByte))))
    val fee     = calcDataFee(entries, 1)
    for (i <- 1 to 8) {
      sender.putData(sender.keyPair, entries, fee + i)
    }
    sender.waitFor("empty utx")(n => n.utxSize, (utxSize: Int) => utxSize == 0, 100.millis)
  }
}