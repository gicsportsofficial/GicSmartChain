package com.gicsports.it.sync

import com.typesafe.config.{Config, ConfigFactory}
import com.gicsports.account.AddressOrAlias
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.api.TransactionInfo
import com.gicsports.it.{BaseFreeSpec, WaitForHeight2}
import com.gicsports.test._
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.transfer.TransferTransaction

class NodeRestartTestSuite extends BaseFreeSpec with WaitForHeight2 {
  import NodeRestartTestSuite._

  override protected def nodeConfigs: Seq[Config] = Configs

  private def nodeA = nodes.head
  private def nodeB = nodes(1)

  "node should grow up to 5 blocks together and sync" in {
    nodes.waitForSameBlockHeadersAt(height = 5)
  }

  "create many addresses and check them after node restart" in {
    1 to 10 map (_ => nodeA.createKeyPair())
    val setOfAddresses      = nodeA.getAddresses
    val nodeAWithOtherPorts = docker.restartContainer(dockerNodes().head)
    val maxHeight           = nodes.map(_.height).max
    nodeAWithOtherPorts.getAddresses should contain theSameElementsAs setOfAddresses
    nodes.waitForSameBlockHeadersAt(maxHeight + 2)
  }

  "after restarting all the nodes, the duplicate transaction cannot be put into the blockchain" in {
    val txJson = TransferTransaction
      .selfSigned(
        1.toByte,
        nodeB.keyPair,
        AddressOrAlias.fromString(nodeA.address).explicitGet(),
        Waves,
        1.waves,
        Waves,
        minFee,
        ByteStr.empty,
        System.currentTimeMillis()
      )
      .explicitGet()
      .json()

    val tx = nodeB.signedBroadcast(txJson, waitForTx = true)
    nodeA.waitForTransaction(tx.id)

    val txHeight = nodeA.transactionInfo[TransactionInfo](tx.id).height

    nodes.waitForHeightArise()

    docker.restartContainer(dockerNodes().head)
    docker.restartContainer(dockerNodes()(1))

    nodes.waitForHeight(txHeight + 2)

    assertBadRequestAndMessage(
      nodeB.signedBroadcast(txJson, waitForTx = true),
      s"State check failed. Reason: Transaction ${tx.id} is already in the state on a height of $txHeight"
    )
  }

}

object NodeRestartTestSuite {
  import com.gicsports.it.NodeConfigs._
  private val FirstNode = ConfigFactory.parseString(s"""
                                                         |GIC {
                                                         |  synchronization.synchronization-timeout = 10s
                                                         |  blockchain.custom.functionality {
                                                         |    pre-activated-features.1 = 0
                                                         |  }
                                                         |  miner.quorum = 0
                                                         |  wallet {
                                                         |     file = "/tmp/wallet.dat"
                                                         |     password = "bla"
                                                         |  }
                                                         |
                                                         |}""".stripMargin)

  private val SecondNode = ConfigFactory.parseString(s"""
                                                            |GIC {
                                                            |  synchronization.synchronization-timeout = 10s
                                                            |  blockchain.custom.functionality {
                                                            |    pre-activated-features.1 = 0
                                                            |  }
                                                            |  miner.enable = no
                                                            |  wallet {
                                                            |     file = "/tmp/wallet.dat"
                                                            |     password = "bla"
                                                            |  }
                                                            |}""".stripMargin)

  val Configs: Seq[Config] = Seq(
    FirstNode.withFallback(Default.head),
    SecondNode.withFallback(Default(1))
  )

}
