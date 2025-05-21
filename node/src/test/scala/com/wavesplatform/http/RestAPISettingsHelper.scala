package com.gicsports.http

import com.typesafe.config.ConfigFactory
import com.gicsports.api.http.`X-Api-Key`
import com.gicsports.common.utils.Base58
import com.gicsports.crypto
import com.gicsports.settings._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

trait RestAPISettingsHelper {
  private val apiKey: String = "test_api_key"

  val ApiKeyHeader = `X-Api-Key`(apiKey)

  lazy val MaxTransactionsPerRequest = 10000
  lazy val MaxAddressesPerRequest    = 10000
  lazy val MaxKeysPerRequest         = 1000
  lazy val MaxAssetIdsPerRequest     = 100

  lazy val restAPISettings = {
    val keyHash = Base58.encode(crypto.secureHash(apiKey.getBytes("UTF-8")))
    ConfigFactory
      .parseString(
        s"""GIC.rest-api {
           |  api-key-hash = $keyHash
           |  transactions-by-address-limit = $MaxTransactionsPerRequest
           |  distribution-address-limit = $MaxAddressesPerRequest
           |  data-keys-request-limit = $MaxKeysPerRequest
           |  asset-details-limit = $MaxAssetIdsPerRequest
           |}
         """.stripMargin
      )
      .withFallback(ConfigFactory.load())
      .as[RestAPISettings]("GIC.rest-api")
  }
}
