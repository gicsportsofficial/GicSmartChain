package com.gicsports.state.diffs.smart.eth

import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.history.Domain
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.diffs.FeeValidation.{FeeConstants, FeeUnit}
import com.gicsports.test.*
import com.gicsports.transaction.Asset
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction.TransactionType.Transfer
import com.gicsports.transaction.TxHelpers.*
import com.gicsports.transaction.utils.EthConverters.*
import com.gicsports.transaction.utils.EthTxGenerator
import com.gicsports.utils.EthHelpers

class EthereumTransferFeeTest extends PropSpec with WithDomain with EthHelpers {
  import DomainPresets.*

  private val transferFee      = FeeConstants(Transfer) * FeeUnit
  private val transferSmartFee = transferFee + ScriptExtraFee

  property("smart asset should require additional fee") {
    val assetScript = TestCompiler(V5).compileExpression("true")
    val issueTx     = issue(script = Some(assetScript))
    val asset       = IssuedAsset(issueTx.id())
    val preTransfer = transfer(to = defaultSigner.toEthWavesAddress, asset = asset)
    withDomain(RideV6, Seq(AddrWithBalance(defaultSigner.toEthWavesAddress))) { d =>
      d.appendBlock(issueTx, preTransfer)
      assertMinFee(d, asset, transferSmartFee)
    }
  }

  property("non-smart asset should require standard fee") {
    val issueTx     = issue()
    val asset       = IssuedAsset(issueTx.id())
    val preTransfer = transfer(to = defaultSigner.toEthWavesAddress, asset = asset)
    withDomain(RideV6, Seq(AddrWithBalance(defaultSigner.toEthWavesAddress))) { d =>
      d.appendBlock(issueTx, preTransfer)
      assertMinFee(d, asset, transferFee)
    }
  }

  property("Waves should require standard fee") {
    withDomain(RideV6, Seq(AddrWithBalance(defaultSigner.toEthWavesAddress))) { d =>
      assertMinFee(d, Waves, transferFee)
    }
  }

  private def assertMinFee(d: Domain, asset: Asset, fee: Long) = {
    val notEnoughFeeTx = EthTxGenerator.generateEthTransfer(defaultSigner.toEthKeyPair, secondAddress, 1, asset, fee = fee - 1)
    val enoughFeeTx    = EthTxGenerator.generateEthTransfer(defaultSigner.toEthKeyPair, secondAddress, 1, asset, fee = fee)
    d.appendBlockE(notEnoughFeeTx) should produce(s"does not exceed minimal value of $fee GIC")
    d.appendAndAssertSucceed(enoughFeeTx)
  }
}
