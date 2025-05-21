package com.gicsports.transaction

import com.gicsports.NTPTime
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithDomain
import com.gicsports.db.WithState.AddrWithBalance
import com.gicsports.features.BlockchainFeatures
import com.gicsports.lang.directives.values.{V4, V5}
import com.gicsports.lang.v1.compiler.Terms.{CONST_BYTESTR, CONST_STRING}
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.*
import com.gicsports.test.DomainPresets.*

class InvokeScriptComplexitySpec extends FreeSpec with WithDomain with NTPTime {
  private[this] val dApp1 = TestCompiler(V5).compileContract("""
      |{-# STDLIB_VERSION 5 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |
      |@Callable(i)
      |func call(k: String, childAddress: ByteVector, assetId: ByteVector) = {
      |    let key = takeRight(toBase58String(keccak256_16Kb((k+"x").toBytes())), 15)
      |    let payment = AttachedPayment(assetId, 1)
      |    strict z = invoke(Address(childAddress), "call", [key, assetId],[payment])
      |    let val1 = match (z) {
      |        case t:String => takeRight(toBase58String(sha256_16Kb(("x" + t).toBytes())), 50)
      |        case _ => "2"
      |      }
      |    ([StringEntry(key, val1)], val1)
      |}
      |""".stripMargin)

  private[this] val dApp0 = TestCompiler(V5).compileContract("""
      |{-# STDLIB_VERSION 5 #-}
      |{-# CONTENT_TYPE DAPP #-}
      |{-# SCRIPT_TYPE ACCOUNT #-}
      |
      |@Callable(i)
      |func call(k: String, assetId:ByteVector) = {
      |
      |    ([
      |      StringEntry(k, k),
      |      BinaryEntry("asset", assetId)
      |    ], k
      |    )
      |}
      |""".stripMargin)

  private[this] val smartAssetScript =
    TestCompiler(V4).compileAsset(
      s"""{-# STDLIB_VERSION 4 #-}
         |{-# CONTENT_TYPE EXPRESSION #-}
         |{-# SCRIPT_TYPE ASSET #-}
         |
         |let message = base58'emsY'
         |let pub = base58'HnU9jfhpMcQNaG5yQ46eR43RnkWKGxerw2zVrbpnbGof'
         |let sig = base58'4uXfw7162zaopAkTNa7eo6YK2mJsTiHGJL3dCtRRH63z1nrdoHBHyhbvrfZovkxf2jKsi2vPsaP2XykfZmUiwPeg'
         |
         |${Seq.fill(16)("sigVerify(message, sig, pub)").mkString(" && ")}
         |""".stripMargin
    )

  private[this] val settings = domainSettingsWithFS(
    SettingsFromDefaultConfig.blockchainSettings.functionalitySettings.copy(preActivatedFeatures = BlockchainFeatures.implemented.map {
      case v @ BlockchainFeatures.FeeSponsorship.id =>
        v -> -SettingsFromDefaultConfig.blockchainSettings.functionalitySettings.featureCheckBlocksPeriod
      case f => f -> 0
    }.toMap)
  )

  "correctly estimates complexity when child dApp invocation involves payment in smart asset" in {
    val invoker = TxHelpers.signer(0)
    val dApp0KP = TxHelpers.signer(1)
    val dApp1KP = TxHelpers.signer(2)

    val balances = Seq(invoker, dApp0KP, dApp1KP).map(acc => AddrWithBalance(acc.toAddress, 10000.waves))

    withDomain(settings, balances) { d =>
      val utx = d.utxPool

      val issueTx = TxHelpers.issue(issuer = dApp1KP, amount = 1000_00, script = Some(smartAssetScript))

      d.appendBlock(
        TxHelpers.setScript(dApp0KP, dApp0),
        TxHelpers.setScript(dApp1KP, dApp1),
        issueTx
      )

      val invocation = TxHelpers.invoke(
        dApp = dApp1KP.toAddress,
        func = Some("call"),
        args = Seq(
          CONST_STRING("aaa").explicitGet(),
          CONST_BYTESTR(ByteStr(dApp0KP.toAddress.bytes)).explicitGet(),
          CONST_BYTESTR(issueTx.id()).explicitGet()
        ),
        invoker = invoker
      )

      utx.putIfNew(invocation, forceValidate = true).resultE.explicitGet() shouldBe true
      utx.size shouldBe 1

      utx.cleanUnconfirmed()

      utx.size shouldBe 1

      utx.close()
    }
  }
}
