package com.gicsports.state.diffs.ci

import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.test.*
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.utils.Signed
import com.gicsports.transaction.{TxHelpers, TxVersion}

class IllegalAddressChainIdTest extends PropSpec with WithDomain {
  import DomainPresets.*

  private[this] def sigVerify(c: Boolean): String =
    s""" strict c = ${if (c) (1 to 5).map(_ => "sigVerify(base58'', base58'', base58'')").mkString(" || ") else "true"} """

  private[this] def contract(bigComplexity: Boolean) = TestCompiler(V5).compileContract(
    s"""
       |  @Callable(i)
       |  func default() = {
       |    ${sigVerify(bigComplexity)}
       |    let address = Address(base58'3PMj3yGPBEa1Sx9X4TSBFeJCMMaE3wvKR4N')
       |    [ ScriptTransfer(address, 1, unit) ]
       |  }
     """.stripMargin
  )

  private[this] def scenario(fail: Boolean, bigComplexity: Boolean = false) = {
    val master   = RandomKeyPair()
    val invoker  = RandomKeyPair()
    val gTx1     = TxHelpers.genesis(master.toAddress, ENOUGH_AMT, TxHelpers.timestamp)
    val gTx2     = TxHelpers.genesis(invoker.toAddress, ENOUGH_AMT, TxHelpers.timestamp)
    val ssTx     = SetScriptTransaction.selfSigned(1.toByte, master, Some(contract(bigComplexity)), 1.waves, TxHelpers.timestamp).explicitGet()
    val invokeTx = Signed.invokeScript(TxVersion.V3, invoker, master.toAddress, None, Nil, 0.06.waves, Waves, TxHelpers.timestamp)
    (Seq(gTx1, gTx2, ssTx), invokeTx)
  }

  private val error = "Address belongs to another network: expected: 84(T), actual: 87(W)"

  property("no fail before fix") {
    withDomain(RideV5) { d =>
      val (genesisTxs, invokeTx) = scenario(fail = true)
      d.appendBlock(genesisTxs*)
      intercept[Exception](d.appendBlock(invokeTx)).getMessage should include(error)
    }
  }

  property("reject after fix") {
    withDomain(RideV6) { d =>
      val (genesisTxs, invokeTx) = scenario(fail = true)
      d.appendBlock(genesisTxs*)
      d.appendAndCatchError(invokeTx).toString should include(error)
    }
  }

  property("fail after fix and big complexity") {
    withDomain(RideV6) { d =>
      val (genesisTxs, invokeTx) = scenario(fail = true, bigComplexity = true)
      d.appendBlock(genesisTxs*)
      d.appendAndAssertFailed(invokeTx)
    }
  }
}
