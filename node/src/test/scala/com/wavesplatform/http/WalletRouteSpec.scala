package com.gicsports.http

import com.gicsports.TestWallet
import com.gicsports.api.http.ApiError.ApiKeyNotValid
import com.gicsports.api.http.WalletApiRoute
import com.gicsports.common.utils.Base58
import play.api.libs.json.JsObject

class WalletRouteSpec extends RouteSpec("/wallet") with RestAPISettingsHelper with TestWallet {
  private val route = WalletApiRoute(restAPISettings, testWallet).route

  private val brokenRestApiSettings     = restAPISettings.copy(apiKeyHash = "InvalidAPIKeyHash")
  private val routeWithIncorrectKeyHash = WalletApiRoute(brokenRestApiSettings, testWallet).route

  routePath("/seed") - {
    "requires api-key header" in {
      Get(routePath("/seed")) ~> route should produce(ApiKeyNotValid)
    }

    "returns seed when api-key header is present" in {
      Get(routePath("/seed")) ~> ApiKeyHeader ~> route ~> check {
        (responseAs[JsObject] \ "seed").as[String] shouldEqual Base58.encode(testWallet.seed)
      }
    }

    "doesn't work if invalid api-key-hash was set" in {
      Get(routePath("/seed")) ~> ApiKeyHeader ~> routeWithIncorrectKeyHash should produce(ApiKeyNotValid)
    }
  }
}
