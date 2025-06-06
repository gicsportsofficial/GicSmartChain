package com.gicsports.generator

import java.net.{InetSocketAddress, URL}
import java.nio.charset.StandardCharsets

import cats.Show
import cats.implicits.showInterpolator
import com.google.common.primitives.{Bytes, Ints}
import com.gicsports.account.KeyPair
import com.gicsports.generator.GeneratorSettings.NodeAddress

case class GeneratorSettings(
    chainId: String,
    accounts: Seq[String],
    sendTo: Seq[NodeAddress],
    worker: Worker.Settings,
    mode: Mode.Value,
    narrow: NarrowTransactionGenerator.Settings,
    wide: WideTransactionGenerator.Settings,
    dynWide: DynamicWideTransactionGenerator.Settings,
    multisig: MultisigTransactionGenerator.Settings,
    oracle: OracleTransactionGenerator.Settings,
    swarm: SmartGenerator.Settings
) {
  val addressScheme: Char              = chainId.head
  val privateKeyAccounts: Seq[KeyPair] = accounts.map(s => GeneratorSettings.toKeyPair(s))
}

object GeneratorSettings {
  case class NodeAddress(networkAddress: InetSocketAddress, apiAddress: URL)

  implicit val toPrintable: Show[GeneratorSettings] = { x =>
    import x._

    val modeSettings: String = (mode: @unchecked) match {
      case Mode.NARROW   => show"$narrow"
      case Mode.WIDE     => show"$wide"
      case Mode.DYN_WIDE => show"$dynWide"
      case Mode.MULTISIG => show"$multisig"
      case Mode.ORACLE   => show"$oracle"
      case Mode.SWARM    => show"$swarm"
    }

    s"""network byte: $chainId
       |rich accounts:
       |  ${accounts.mkString("\n  ")}
       |recipient nodes:
       |  ${sendTo.mkString("\n  ")}
       |worker:
       |  ${show"$worker".split('\n').mkString("\n  ")}
       |mode: $mode
       |$mode settings:
       |  ${modeSettings.split('\n').mkString("\n  ")}""".stripMargin
  }

  def toKeyPair(seedText: String): KeyPair = {
    KeyPair(com.gicsports.crypto.secureHash(Bytes.concat(Ints.toByteArray(0), seedText.getBytes(StandardCharsets.UTF_8))))
  }
}
