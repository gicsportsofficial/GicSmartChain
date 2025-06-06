package com.gicsports.features

import com.gicsports.block.Block
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.lang.directives.values._
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.mining.MiningConstraints.MaxScriptsComplexityInBlock
import com.gicsports.mining._
import com.gicsports.state.diffs.BlockDiffer
import com.gicsports.test.DomainPresets.SettingsFromDefaultConfig
import com.gicsports.test._
import com.gicsports.transaction.TxHelpers
import org.scalamock.scalatest.PathMockFactory

class RideV5LimitsChangeTest extends FlatSpec with WithDomain with PathMockFactory {
  "Blockchain" should "reject block with >1kk complexity before SynchronousCalls activated" in {
    val contractSigner  = TxHelpers.secondSigner
    val contractAddress = contractSigner.toAddress

    withDomain(DomainPresets.RideV4, Seq(AddrWithBalance(TxHelpers.defaultAddress), AddrWithBalance(contractAddress, 10.waves))) { d =>
      val setScript = TxHelpers.setScript(contractSigner, contract)
      d.appendBlock(setScript)

      val invokes = for (_ <- 1 to 273) yield TxHelpers.invoke(contractAddress) // 3675 complexity, 1003275 total

      val block = d.createBlock(Block.ProtoBlockVersion, invokes, strictTime = true)
      val differResult = BlockDiffer.fromBlock(
        d.blockchain,
        Some(d.lastBlock),
        block,
        MiningConstraints(d.blockchain, d.blockchain.height, Some(SettingsFromDefaultConfig.minerSettings)).total,
        block.header.generationSignature
      )
      differResult should produce("Limit of txs was reached")
    }
  }

  it should "accept block with 2.5kk complexity after SynchronousCalls activated" in {
    val contractSigner  = TxHelpers.secondSigner
    val contractAddress = contractSigner.toAddress

    withDomain(DomainPresets.RideV5, Seq(AddrWithBalance(TxHelpers.defaultAddress), AddrWithBalance(contractAddress, 10.waves))) { d =>
      val setScript = TxHelpers.setScript(contractSigner, contract)
      d.appendBlock(setScript)

      val invokesCount     = 680
      val invokeComplexity = 3620
      val invokes          = for (_ <- 1 to invokesCount) yield TxHelpers.invoke(contractAddress)

      val time = new TestTime()

      val block = d.createBlock(Block.ProtoBlockVersion, invokes, strictTime = true)
      val differResult = BlockDiffer
        .fromBlock(
          d.blockchain,
          Some(d.lastBlock),
          block,
          MiningConstraints(d.blockchain, d.blockchain.height, Some(SettingsFromDefaultConfig.minerSettings)).total,
          block.header.generationSignature
        )
        .explicitGet()
      differResult.constraint.asInstanceOf[MultiDimensionalMiningConstraint].constraints.head shouldBe OneDimensionalMiningConstraint(
        rest = MaxScriptsComplexityInBlock.AfterRideV5 - invokesCount * invokeComplexity,
        TxEstimators.scriptsComplexity,
        "MaxScriptsComplexityInBlock"
      )

      time.setTime(block.header.timestamp)
      d.appendBlock(block)
      d.blockchain.height shouldBe 3
    }
  }

  private[this] val contract: Script =
    TestCompiler(V4).compileContract(
      s"""
         | {-#STDLIB_VERSION 4 #-}
         | {-#SCRIPT_TYPE ACCOUNT #-}
         | {-#CONTENT_TYPE DAPP #-}
         |
         | @Callable(tx)
         | func default() =
         |   if (${"sigVerify(base58'', base58'', base58'') ||" * 18} false) then []
         |   else []
         |
         | @Verifier(tx)
         | func verify() =
         |   ${"sigVerify(base58'', base58'', base58'') ||" * 9} true
      """.stripMargin
    )
}
