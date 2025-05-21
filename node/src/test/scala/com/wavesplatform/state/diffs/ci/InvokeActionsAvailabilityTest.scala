package com.gicsports.state.diffs.ci

import com.gicsports.account.Address
import com.gicsports.common.state.ByteStr
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.db.{DBCacheSettings, WithDomain}
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.test.*
import com.gicsports.transaction.Asset.IssuedAsset
import com.gicsports.transaction.smart.InvokeScriptTransaction.Payment
import com.gicsports.transaction.TxHelpers
import org.scalatest.{EitherValues, Inside}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class InvokeActionsAvailabilityTest
    extends PropSpec
    with ScalaCheckPropertyChecks
    with Inside
    with DBCacheSettings
    with WithDomain
    with EitherValues {
  import DomainPresets.*

  private val transferAmount       = 100
  private val issueAmount          = 200
  private val reissueAmount        = 40
  private val burnAmount           = 80
  private val leaseAmount          = 500
  private val cancelledLeaseAmount = 1234

  private def proxyDAppScript(callingDApp: Address): Script = {
    val assetActionsCheck =
      s"""
         | let assetId = dApp2.getBinaryValue("assetId")
         | strict c3 = if (dApp2.assetBalance(assetId) == $issueAmount + $reissueAmount - $burnAmount) then true else throw("Asset actions error")
       """.stripMargin

    val leaseActionsCheck =
      s"""
         | strict c4 = if (
         |   this.wavesBalance().effective == startBalance.effective + $leaseAmount + $transferAmount       &&
         |   dApp2.wavesBalance().available == startDApp2Balance.available - $leaseAmount - $transferAmount
         | ) then true else throw("Lease actions error")
       """.stripMargin

    TestCompiler(V5).compileContract(
      s"""
         | {-# STDLIB_VERSION 5       #-}
         | {-# CONTENT_TYPE   DAPP    #-}
         | {-# SCRIPT_TYPE    ACCOUNT #-}
         |
         | let dApp2 = Address(base58'$callingDApp')
         |
         | @Callable(inv)
         | func default() = {
         |    strict startBalance = this.wavesBalance()
         |    strict startDApp2Balance = dApp2.wavesBalance()
         |    strict r = dApp2.invoke("default", nil, [])
         |    strict c1 = if (dApp2.getStringValue("key") == "value") then true else throw("Data error")
         |    strict c2 = if (this.wavesBalance().regular == startBalance.regular + $transferAmount) then true else throw("Transfer error")
         |    $assetActionsCheck
         |    $leaseActionsCheck
         |    []
         | }
       """.stripMargin
    )
  }

  private val callingDAppScript: Script =
    TestCompiler(V5).compileContract(
      s"""
         | {-# CONTENT_TYPE   DAPP          #-}
         | {-# SCRIPT_TYPE    ACCOUNT       #-}
         |
         | @Callable(i)
         | func default() = {
         |   let issue = Issue("new asset", "", $issueAmount, 8, true, unit, 0)
         |   let assetId = calculateAssetId(issue)
         |   let actions =
         |   [
         |     StringEntry("key", "value"),
         |     BinaryEntry("assetId", assetId),
         |     ScriptTransfer(i.caller, $transferAmount, unit),
         |     issue,
         |     Reissue(assetId, $reissueAmount, true),
         |     Burn(assetId, $burnAmount)
         |   ]
         |   let leaseToCancel = Lease(i.caller, $cancelledLeaseAmount)
         |   actions ++ [
         |      Lease(i.caller, $leaseAmount),
         |      leaseToCancel,
         |      LeaseCancel(calculateLeaseId(leaseToCancel))
         |   ]
         | }
       """.stripMargin
    )

  private val paymentAmount = 12345

  property("actions availability in sync call") {
    val invoker     = TxHelpers.signer(0)
    val callingDApp = TxHelpers.signer(1)
    val proxyDApp   = TxHelpers.signer(2)

    val balances = AddrWithBalance.enoughBalances(invoker, callingDApp, proxyDApp)

    val issue                = TxHelpers.issue(invoker, ENOUGH_AMT)
    val setScriptCallingDApp = TxHelpers.setScript(callingDApp, callingDAppScript)
    val setScriptProxyDApp   = TxHelpers.setScript(proxyDApp, proxyDAppScript(callingDApp.toAddress))
    val asset                = IssuedAsset(issue.id.value())
    val payments             = Seq(Payment(paymentAmount, asset))
    val preparingTxs         = Seq(issue, setScriptCallingDApp, setScriptProxyDApp)
    val invoke               = TxHelpers.invoke(proxyDApp.toAddress, func = None, invoker = invoker, payments = payments, fee = 5000.1.waves)

    withDomain(RideV5, balances) { d =>
      d.appendBlock(preparingTxs*)

      val startProxyDAppBalance   = d.blockchain.balance(proxyDApp.toAddress)
      val startCallingDAppBalance = d.blockchain.balance(callingDApp.toAddress)

      d.appendBlock(invoke)
      d.blockchain.transactionSucceeded(invoke.id.value()) shouldBe true

      d.blockchain.accountData(callingDApp.toAddress, "key").get.value shouldBe "value"
      d.blockchain.balance(proxyDApp.toAddress) shouldBe startProxyDAppBalance + transferAmount
      d.blockchain.balance(callingDApp.toAddress) shouldBe startCallingDAppBalance - transferAmount

      val asset = d.blockchain.accountData(callingDApp.toAddress, "assetId").get.value.asInstanceOf[ByteStr]
      d.blockchain.balance(callingDApp.toAddress, IssuedAsset(asset)) shouldBe issueAmount + reissueAmount - burnAmount

      d.blockchain.effectiveBalance(callingDApp.toAddress, 0) shouldBe startCallingDAppBalance - transferAmount - leaseAmount
      d.blockchain.effectiveBalance(proxyDApp.toAddress, 0) shouldBe startProxyDAppBalance + transferAmount + leaseAmount
    }
  }
}
