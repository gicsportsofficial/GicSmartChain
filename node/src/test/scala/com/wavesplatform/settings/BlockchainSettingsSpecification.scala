package com.gicsports.settings

import com.typesafe.config.ConfigFactory
import com.gicsports.common.state.ByteStr
import com.gicsports.test.FlatSpec

import scala.concurrent.duration._

class BlockchainSettingsSpecification extends FlatSpec {
  "BlockchainSettings" should "read custom values" in {
    val config   = loadConfig(ConfigFactory.parseString("""GIC {
        |  directory = "/gic"
        |  data-directory = "/gic/data"
        |  blockchain {
        |    type = CUSTOM
        |    custom {
        |      address-scheme-character = "C"
        |      functionality {
        |        feature-check-blocks-period = 10000
        |        blocks-for-feature-activation = 9000
        |        generation-balance-depth-from-50-to-1000-after-height = 4
        |        block-version-3-after-height = 18
        |        pre-activated-features {
        |          1 = 0
        |          2 = 0
        |          3 = 0
        |          5 = 0
        |          6 = 0
        |        }
        |        double-features-periods-after-height = 21
        |        max-transaction-time-back-offset = 55s
        |        max-transaction-time-forward-offset = 12d
        |        lease-expiration = 1000000
        |      }
        |      rewards {
        |        term = 100000
        |        initial = 600000000
        |        min-increment = 50000000
        |        voting-interval = 10000
        |      }
        |      genesis {
        |        timestamp: 1517503972000
        | 		   block-timestamp: 1517503972000
        |        signature = "BASE58BLKSGNATURE"
        |        initial-balance = 50000000000000000
        |        initial-base-target = 153722867
        |        average-block-delay = 60s
        |        transactions = [
        |          {recipient = "BASE58ADDRESS1", amount = 50000000000000000},
        |        ]
        |      }
        |    }
        |  }
        |}""".stripMargin))
    val settings = BlockchainSettings.fromRootConfig(config)

    settings.addressSchemeCharacter should be('C')
    settings.functionalitySettings.featureCheckBlocksPeriod should be(10000)
    settings.functionalitySettings.blocksForFeatureActivation should be(9000)
    settings.functionalitySettings.generationBalanceDepthFrom50To1000AfterHeight should be(4)
    settings.functionalitySettings.blockVersion3AfterHeight should be(18)
    settings.functionalitySettings.preActivatedFeatures should be(Map(5 -> 0, 1 -> 0, 6 -> 0, 2 -> 0, 3 -> 0))
    settings.functionalitySettings.doubleFeaturesPeriodsAfterHeight should be(21)
    settings.functionalitySettings.maxTransactionTimeBackOffset should be(55.seconds)
    settings.functionalitySettings.maxTransactionTimeForwardOffset should be(12.days)
    settings.rewardsSettings.initial should be(600000000)
    settings.rewardsSettings.minIncrement should be(50000000)
    settings.rewardsSettings.term should be(100000)
    settings.rewardsSettings.votingInterval should be(10000)
    settings.genesisSettings.blockTimestamp should be(1517503972000L)
    settings.genesisSettings.timestamp should be(1517503972000L)
    settings.genesisSettings.signature should be(ByteStr.decodeBase58("BASE58BLKSGNATURE").toOption)
    settings.genesisSettings.initialBalance should be(50000000000000000L)
    settings.genesisSettings.initialBaseTarget should be(153722867)
    settings.genesisSettings.averageBlockDelay should be(60.seconds)
    settings.genesisSettings.transactions should be(Seq(GenesisTransactionSettings("BASE58ADDRESS1", 50000000000000000L)))
  }

  it should "read testnet settings" in {
    val config   = loadConfig(ConfigFactory.parseString("""GIC {
        |  directory = "/gic"
        |  data-directory = "/gic/data"
        |  blockchain {
        |    type = TESTNET
        |  }
        |}""".stripMargin))
    val settings = BlockchainSettings.fromRootConfig(config)

    settings.addressSchemeCharacter should be('l')
    settings.functionalitySettings.generationBalanceDepthFrom50To1000AfterHeight should be(0)
    settings.functionalitySettings.blockVersion3AfterHeight should be(0)
    settings.functionalitySettings.maxTransactionTimeBackOffset should be(120.minutes)
    settings.functionalitySettings.maxTransactionTimeForwardOffset should be(90.minutes)
    settings.rewardsSettings.initial should be(600000000)
    settings.rewardsSettings.minIncrement should be(50000000)
    settings.rewardsSettings.term should be(100000)
    settings.rewardsSettings.votingInterval should be(10000)
    settings.genesisSettings.blockTimestamp should be(1500635421931L)
    settings.genesisSettings.timestamp should be(1500635421931L)
    settings.genesisSettings.signature should be(
      ByteStr.decodeBase58("5E3xfYy3Mdo6XvqnWyQjRjyyBpssCKn6uJXmy4tvmpR4ZY8tMJDVHX282bxm192FNsWGfXM7DiT1Kh8YyJfWa1t9").toOption)
    settings.genesisSettings.initialBalance should be(10000000000000000L)

    settings.genesisSettings.transactions should be(
      Seq(
        GenesisTransactionSettings("3XrUtvRZ6LLU8F2wwkuDffwTuLUNcpnjthB", 9000000000000000L),
        GenesisTransactionSettings("3XqUDqCLK8knT96iFqR91uL4gvGkFiw39Bh", 1000000000000000L),

      ))
  }

  it should "read mainnet settings" in {
    val config   = loadConfig(ConfigFactory.parseString("""GIC {
        |  directory = "/gic"
        |  data-directory = "/gic/data"
        |  blockchain {
        |    type = MAINNET
        |  }
        |}""".stripMargin))
    val settings = BlockchainSettings.fromRootConfig(config)

    settings.addressSchemeCharacter should be('L')
    settings.functionalitySettings.generationBalanceDepthFrom50To1000AfterHeight should be(0L)
    settings.functionalitySettings.maxTransactionTimeBackOffset should be(120.minutes)
    settings.functionalitySettings.maxTransactionTimeForwardOffset should be(90.minutes)
    settings.rewardsSettings.initial should be(600000000)
    settings.rewardsSettings.minIncrement should be(50000000)
    settings.rewardsSettings.term should be(100000)
    settings.rewardsSettings.votingInterval should be(10000)
    settings.genesisSettings.blockTimestamp should be(1500635421931L)
    settings.genesisSettings.timestamp should be(1500635421931L)
    settings.genesisSettings.signature should be(
      ByteStr.decodeBase58("4UpaXRasizJcaYjV8PndCFAXMftC3yZVvGiTft9c5HiXX5jj5eJ1Xo95Lerg6X8diKzi1dywvyfZYJipif1oYgZD").toOption)
    settings.genesisSettings.initialBalance should be(10000000000000000L)
    settings.genesisSettings.transactions should be(
      Seq(
        GenesisTransactionSettings("3JhF7aMPXBYtJ84iwX5e3N9W5JmZRSgHPy9", 1000000000000000L),
        GenesisTransactionSettings("3JqAYiRnuiJxdMVmdTUsxuTV39LXHR5JWXk", 5000000000000000L),
        GenesisTransactionSettings("3JeXZJAU1onkoiMCKT2i5LxMXWe7aRB7daL", 1000000000000000L),
        GenesisTransactionSettings("3Jf2GXsAExpfhbcPg6NJAdaF7EhX176rb4B", 1000000000000000L),
        GenesisTransactionSettings("3JzWq595aZxaU2Jkexsb8N6XWDPYoi1wzCL", 1000000000000000L),
        GenesisTransactionSettings("3JjJuwvTcQKCq7H53H1XZXNy7Up1syjrRng", 1000000000000000L)
      )
    )

  }
}
