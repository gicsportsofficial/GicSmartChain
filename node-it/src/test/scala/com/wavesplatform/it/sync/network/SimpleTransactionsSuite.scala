package com.gicsports.it.sync.network

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.gicsports.account.Address
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.NodeConfigs
import com.gicsports.it.api.AsyncNetworkApi._
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.sync._
import com.gicsports.it.transactions.BaseTransactionSuite
import com.gicsports.network.{RawBytes, TransactionSpec}
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.transfer._

import scala.concurrent.duration._

class SimpleTransactionsSuite extends BaseTransactionSuite {
  override protected def nodeConfigs: Seq[Config] =
    NodeConfigs.newBuilder
      .overrideBase(_.quorum(0))
      .withDefault(entitiesNumber = 1)
      .buildNonConflicting()

  private def node = nodes.head

  test("valid tx send by network to node should be in blockchain") {
    val tx = TransferTransaction
      .selfSigned(
        1.toByte,
        node.keyPair,
        Address.fromString(node.address).explicitGet(),
        Waves,
        1L,
        Waves,
        minFee,
        ByteStr.empty,
        System.currentTimeMillis()
      )
      .explicitGet()

    node.sendByNetwork(RawBytes.fromTransaction(tx))
    node.waitForTransaction(tx.id().toString)

  }

  test("invalid tx send by network to node should be not in UTX or blockchain") {
    val tx = TransferTransaction
      .selfSigned(
        1.toByte,
        node.keyPair,
        Address.fromString(node.address).explicitGet(),
        Waves,
        1L,
        Waves,
        minFee,
        ByteStr.empty,
        System.currentTimeMillis() + (1 days).toMillis
      )
      .explicitGet()

    node.sendByNetwork(RawBytes.fromTransaction(tx))
    val maxHeight = nodes.map(_.height).max
    nodes.waitForHeight(maxHeight + 1)
    node.ensureTxDoesntExist(tx.id().toString)
  }

  test("should blacklist senders of non-parsable transactions") {
    val blacklistBefore = node.blacklistedPeers
    node.sendByNetwork(RawBytes(TransactionSpec.messageCode, "foobar".getBytes(StandardCharsets.UTF_8)))
    node.waitForBlackList(blacklistBefore.size)
  }
}
