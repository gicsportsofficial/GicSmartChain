package com.gicsports.state.patch

import com.gicsports.account.PublicKey
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils._
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures
import com.gicsports.history.Domain
import com.gicsports.settings.WavesSettings
import com.gicsports.test._
import com.gicsports.test.DomainPresets._
import com.gicsports.transaction.TxHelpers
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.BeforeAndAfterAll

class CancelLeasesToDisabledAliasesSpec extends FlatSpec with PathMockFactory with WithDomain with BeforeAndAfterAll {
  val MainnetSettings: WavesSettings = {
    import SettingsFromDefaultConfig.blockchainSettings.{functionalitySettings => fs}
    SettingsFromDefaultConfig.copy(
      blockchainSettings = SettingsFromDefaultConfig.blockchainSettings.copy(
        addressSchemeCharacter = 'W',
        functionalitySettings = fs.copy(preActivatedFeatures = fs.preActivatedFeatures ++ Map(
            BlockchainFeatures.NG.id               -> 0,
            BlockchainFeatures.SmartAccounts.id    -> 0,
            BlockchainFeatures.SynchronousCalls.id -> 2
          ))
      )
    )
  }

  "CancelLeasesToDisabledAliases" should "be applied only once" in
    withDomain(MainnetSettings, AddrWithBalance.enoughBalances(TxHelpers.defaultSigner)) { d =>
      testLeaseBalance(d).out shouldBe 0L

      d.appendKeyBlock()
      testLeaseBalance(d).out shouldBe -2562590821L

      d.appendMicroBlock(TxHelpers.transfer())
      d.appendMicroBlock(TxHelpers.transfer())
      d.appendMicroBlock(TxHelpers.transfer())
      d.appendKeyBlock()
      testLeaseBalance(d).out shouldBe -2562590821L
    }

  it should "be applied on extension apply" in
    withDomain(MainnetSettings, AddrWithBalance.enoughBalances(TxHelpers.defaultSigner)) { d =>
      testLeaseBalance(d).out shouldBe 0L
      d.appendBlock()
      testLeaseBalance(d).out shouldBe -2562590821L
      d.appendBlock()
      testLeaseBalance(d).out shouldBe -2562590821L
      d.appendBlock()
      testLeaseBalance(d).out shouldBe -2562590821L
    }

  private def testLeaseBalance(d: Domain) = {
    d.blockchain.leaseBalance(PublicKey(ByteStr(Base58.decode("6NxhjzayDTd52MJL2r6XupGDb7E1xQW7QppSPqo63gsx"))).toAddress)
  }
}
