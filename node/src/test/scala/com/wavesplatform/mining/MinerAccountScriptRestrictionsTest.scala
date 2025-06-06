package com.gicsports.mining

import com.gicsports.account.KeyPair
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures
import com.gicsports.history.Domain
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.script.ContractScript.ContractScriptImpl
import com.gicsports.lang.script.Script
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.settings.{WalletSettings, WavesSettings}
import com.gicsports.test.*
import com.gicsports.transaction.{TxHelpers, TxVersion}
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.utx.UtxPoolImpl
import com.gicsports.wallet.Wallet
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import monix.execution.Scheduler
import monix.reactive.Observable
import DomainPresets.*
import com.gicsports.block.Block
import com.gicsports.lang.ValidationError
import com.gicsports.lang.v1.compiler.Terms.CONST_STRING
import com.gicsports.state.appender.BlockAppender
import monix.eval.Task

import scala.concurrent.duration.*

class MinerAccountScriptRestrictionsTest extends PropSpec with WithDomain {

  type Appender = Block => Task[Either[ValidationError, Option[BigInt]]]

  val time: TestTime            = TestTime()
  val minerAcc: KeyPair         = TxHelpers.signer(1)
  val invoker: KeyPair          = TxHelpers.signer(2)
  val allowedRecipient: KeyPair = TxHelpers.signer(3)

  val dataKey = "testKey"

  property("miner account can have any script after RideV6 feature activation") {
    Seq(
      (dAppScriptWithVerifier, true, true),
      (dAppScriptWithoutVerifier, true, false),
      (accountScript, false, true)
    ).foreach { case (script, hasCallable, hasVerifier) =>
      val checkCallableTxCount = if (hasCallable) 2 else 0
      val checkVerifierTxCount = if (hasVerifier) 1 else 0
      val activationHeight     = 3 + checkCallableTxCount + checkVerifierTxCount

      withDomain(
        DomainPresets.RideV5.setFeaturesHeight((BlockchainFeatures.RideV6, activationHeight)),
        AddrWithBalance.enoughBalances(minerAcc, invoker)
      ) { d =>
        withMiner(d) { (miner, appender, scheduler) =>
          d.appendBlock(setScript(script))
          if (hasCallable) {
            d.appendAndAssertSucceed(
              TxHelpers.invoke(minerAcc.toAddress, Some("c"), Seq(CONST_STRING("invoker").explicitGet()), invoker = invoker)
            )
            d.accountsApi.data(minerAcc.toAddress, dataKey).get.value shouldBe "invoker"
            d.appendAndAssertSucceed(
              TxHelpers.invoke(minerAcc.toAddress, Some("c"), Seq(CONST_STRING("miner").explicitGet()), invoker = minerAcc)
            )
            d.accountsApi.data(minerAcc.toAddress, dataKey).get.value shouldBe "miner"
          }
          if (hasVerifier) {
            d.appendAndAssertSucceed(TxHelpers.transfer(minerAcc, allowedRecipient.toAddress))
            d.appendAndCatchError(TxHelpers.transfer(minerAcc, invoker.toAddress)).toString should include("TransactionNotAllowedByScript")
          }
          miner.getNextBlockGenerationOffset(minerAcc) should produce(errMsgBeforeRideV6)
          forgeAndAppendBlock(d, miner, appender)(scheduler) should produce(errMsgBeforeRideV6)

          d.appendBlock()
          miner.getNextBlockGenerationOffset(minerAcc) should beRight
          forgeAndAppendBlock(d, miner, appender)(scheduler) should beRight
        }
      }
    }
  }

  private def errMsgBeforeRideV6 =
    s"Account(${minerAcc.toAddress}) is scripted and not allowed to forge blocks"

  private def ts: Long = System.currentTimeMillis()

  private def withMiner(d: Domain)(f: (MinerImpl, Appender, Scheduler) => Unit): Unit = {
    val defaultSettings = WavesSettings.default()
    val wavesSettings   = defaultSettings.copy(minerSettings = defaultSettings.minerSettings.copy(quorum = 0))

    val utx = new UtxPoolImpl(
      time,
      d.blockchainUpdater,
      wavesSettings.utxSettings,
      wavesSettings.maxTxErrorLogSize,
      isMiningEnabled = wavesSettings.minerSettings.enable
    )
    val appenderScheduler = Scheduler.singleThread("appender")

    val miner = new MinerImpl(
      new DefaultChannelGroup(GlobalEventExecutor.INSTANCE),
      d.blockchainUpdater,
      wavesSettings,
      time,
      utx,
      Wallet(WalletSettings(None, Some("123"), Some(ByteStr(minerAcc.seed)))),
      d.posSelector,
      Scheduler.singleThread("miner"),
      appenderScheduler,
      Observable.empty
    )

    val appender = BlockAppender(d.blockchainUpdater, time, utx, d.posSelector, appenderScheduler) _

    f(miner, appender, appenderScheduler)
  }

  private def forgeAndAppendBlock(d: Domain, miner: MinerImpl, appender: Appender)(implicit scheduler: Scheduler) = {
    time.setTime(
      d.lastBlock.header.timestamp + d.posSelector
        .getValidBlockDelay(d.blockchain.height, minerAcc, d.lastBlock.header.baseTarget, d.blockchain.generatingBalance(minerAcc.toAddress))
        .explicitGet()
    )
    val forge = miner.forgeBlock(minerAcc)
    val block = forge.explicitGet()._1
    appender(block).runSyncUnsafe(10.seconds)
  }

  private def setScript(script: Script): SetScriptTransaction =
    SetScriptTransaction.selfSigned(TxVersion.V2, minerAcc, Some(script), 1.waves, ts).explicitGet()

  private def verifierScriptStr: String =
    s"""
       |match tx {
       |    case t: TransferTransaction => t.recipient == Address(base58'${allowedRecipient.toAddress}')
       |    case _ => true
       |}
       |""".stripMargin

  private def callableFuncStr: String =
    s"""
       |@Callable(i)
       |func c(value: String) = {
       |  [StringEntry("$dataKey", value)]
       |}""".stripMargin

  private def accountScript: ExprScript =
    TestCompiler(V5).compileExpression(verifierScriptStr)

  private def dAppScriptWithVerifier: ContractScriptImpl = {
    val expr =
      s"""
         |$callableFuncStr
         |
         |@Verifier(tx)
         |func v() = {
         |  $verifierScriptStr
         |}
         |""".stripMargin
    TestCompiler(V5).compileContract(expr)
  }

  private def dAppScriptWithoutVerifier: ContractScriptImpl =
    TestCompiler(V5).compileContract(callableFuncStr)
}
