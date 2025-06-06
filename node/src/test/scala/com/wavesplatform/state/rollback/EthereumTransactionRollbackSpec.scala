package com.gicsports.state.rollback

import com.gicsports.db.WithDomain
import com.gicsports.test.FlatSpec
import com.gicsports.transaction.utils.EthTxGenerator
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.TxHelpers
import com.gicsports.utils.EthHelpers

class EthereumTransactionRollbackSpec extends FlatSpec with WithDomain with EthHelpers {
  "Ethereum transfer" should "rollback" in withDomain(DomainPresets.RideV6) { d =>
    val transaction = EthTxGenerator.generateEthTransfer(TxHelpers.defaultEthSigner, TxHelpers.secondAddress, 1, Waves)

    withClue("genesis") {
      d.helpers.creditWavesToDefaultSigner(1 + 2000000)
      d.balance(TxHelpers.defaultEthAddress) shouldBe (1 + 2000000)
      d.balance(TxHelpers.secondAddress) shouldBe 0
    }

    val (initHeight, initStateSnapshot) = d.makeStateSolid()
    withClue("after transaction") {
      d.appendBlock(transaction)
      d.balance(TxHelpers.defaultEthAddress) shouldBe 0
      d.balance(TxHelpers.secondAddress) shouldBe 1
    }

    withClue("after rollback") {
      d.rollbackTo(initHeight)
      d.balance(TxHelpers.defaultEthAddress) shouldBe (1 + 2000000)
      d.balance(TxHelpers.secondAddress) shouldBe 0
      d.solidStateSnapshot() shouldBe initStateSnapshot
      d.liquidState shouldBe None
    }
  }

  "Ethereum invoke" should "rollback" in withDomain(DomainPresets.RideV6) { d =>
    d.helpers.creditWavesToDefaultSigner()
    d.helpers.creditWavesFromDefaultSigner(TxHelpers.secondAddress)

    val asset  = d.helpers.issueAsset()
    val script = TxHelpers.scriptV5(s"""
        | @Callable(i)
        | func foo() = {
        |   [
        |     IntegerEntry("key", 1),
        |     BooleanEntry("key", true),
        |     StringEntry("key", "str"),
        |     BinaryEntry("key", base58''),
        |     DeleteEntry("key"),
        |     ScriptTransfer(Address(base58'${TxHelpers.secondAddress}'), 1, unit),
        |     ScriptTransfer(Address(base58'${TxHelpers.secondAddress}'), 1, base58'$asset'),
        |     Issue("name", "description", 1000, 4, true, unit, 0),
        |     Reissue(base58'$asset', 1, false),
        |     Burn(base58'$asset', 1),
        |     SponsorFee(base58'$asset', 1)
        |   ]
        | }
        |""".stripMargin)
    d.helpers.setScript(TxHelpers.defaultSigner, script)

    val (initHeight, initStateSnapshot) = d.makeStateSolid()
    val invoke = TxHelpers.invoke(TxHelpers.defaultAddress, Some("foo"), fee = 1000_0600_0000L)
    d.appendBlock(invoke)

    d.rollbackTo(initHeight)
    d.solidStateSnapshot() shouldBe initStateSnapshot
    d.liquidState shouldBe None
  }
}
