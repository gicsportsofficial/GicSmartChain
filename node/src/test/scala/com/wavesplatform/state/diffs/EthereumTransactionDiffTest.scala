package com.gicsports.state.diffs

import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.db.WithDomain
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.directives.values.V6
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.*
import com.gicsports.test.DomainPresets.*
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.utils.EthTxGenerator
import com.gicsports.transaction.TxHelpers
import com.gicsports.transaction.utils.EthConverters.*
import com.gicsports.utils.EthEncoding
import org.web3j.crypto.Bip32ECKeyPair

class EthereumTransactionDiffTest extends PropSpec with WithDomain {

  property(s"public keys with leading zeros are allowed only after ${BlockchainFeatures.ConsensusImprovements} activation") {
    val senderAcc = Bip32ECKeyPair.create(
      EthEncoding.toBytes("0x00db4a036ea48572bf27630c72a1513f48f0b4a6316606fd01c23318befdf984"),
      Array.emptyByteArray
    )

    withDomain(
      DomainPresets.RideV6.setFeaturesHeight(BlockchainFeatures.ConsensusImprovements -> 3),
      Seq(AddrWithBalance(senderAcc.toWavesAddress))
    ) { d =>
      val transfer = EthTxGenerator.generateEthTransfer(senderAcc, senderAcc.toWavesAddress, 1, Waves)
      d.appendAndCatchError(transfer) shouldBe TransactionDiffer.TransactionValidationError(
        GenericError("Sender public key with leading zero byte is not allowed"),
        transfer
      )
      d.appendBlock()
      d.appendAndAssertSucceed(transfer)
    }

    withDomain(
      DomainPresets.RideV6.setFeaturesHeight(BlockchainFeatures.ConsensusImprovements -> 4),
      Seq(AddrWithBalance(senderAcc.toWavesAddress), AddrWithBalance(TxHelpers.defaultAddress))
    ) { d =>
      val invoke = EthTxGenerator.generateEthInvoke(senderAcc, TxHelpers.defaultAddress, "test", Nil, Nil)

      val dApp = TestCompiler(V6).compileContract("""
                                                    |@Callable(i)
                                                    |func test() = []
                                                    |""".stripMargin)

      d.appendBlock(TxHelpers.setScript(TxHelpers.defaultSigner, dApp))

      d.appendAndCatchError(invoke) shouldBe TransactionDiffer.TransactionValidationError(
        GenericError("Sender public key with leading zero byte is not allowed"),
        invoke
      )
      d.appendBlock()
      d.appendAndAssertSucceed(invoke)
    }
  }
}
