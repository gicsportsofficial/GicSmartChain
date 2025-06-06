package com.gicsports.settings

import com.typesafe.config.ConfigFactory
import com.gicsports.test.FlatSpec
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

class UtxSettingsSpecification extends FlatSpec {
  "UTXSettings" should "read values" in {
    val config   = ConfigFactory.parseString("""GIC {
        |  utx {
        |    max-size = 100
        |    max-bytes-size = 100
        |    max-scripted-size = 100
        |    blacklist-sender-addresses = ["a"]
        |    allow-blacklisted-transfer-to = ["b"]
        |    fast-lane-addresses = ["c"]
        |    allow-transactions-from-smart-accounts = false
        |    allow-skip-checks = false
        |    force-validate-in-cleanup = false
        |    always-unlimited-execution = true
        |  }
        |}""".stripMargin).resolve()
    val settings = config.as[UtxSettings]("GIC.utx")
    settings.maxSize shouldBe 100
    settings.maxBytesSize shouldBe 100L
    settings.maxScriptedSize shouldBe 100
    settings.blacklistSenderAddresses shouldBe Set("a")
    settings.allowBlacklistedTransferTo shouldBe Set("b")
    settings.fastLaneAddresses shouldBe Set("c")
    settings.allowTransactionsFromSmartAccounts shouldBe false
    settings.allowSkipChecks shouldBe false
    settings.forceValidateInCleanup shouldBe false
    settings.alwaysUnlimitedExecution shouldBe true
  }
}
