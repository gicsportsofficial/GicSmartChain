package com.gicsports.state

import com.gicsports.db.WithDomain
import com.gicsports.lang.directives.values.V5
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.state.reader.CompositeBlockchain
import com.gicsports.test.*
import com.gicsports.transaction.TxHelpers

class CompositeBlockchainSpec extends FreeSpec with WithDomain {
  "correctly combines asset info" in withDomain(DomainPresets.RideV5) { d =>
    val issuer = RandomKeyPair()
    val permittingScript = TestCompiler(V5).compileAsset("true")
    val issue = TxHelpers.issue(issuer, 100000, 2.toByte, script = Some(permittingScript))
    d.appendBlock(
      TxHelpers.genesis(issuer.toAddress),
      issue
    )

    d.blockchain.assetDescription(issue.asset).flatMap(_.script).map(_.script) should contain (permittingScript)

    val forbiddingScript = TestCompiler(V5).compileAsset("false")

    val cb = CompositeBlockchain(d.blockchain, Diff(assetScripts = Map(issue.asset -> Some(AssetScriptInfo(forbiddingScript, 1)))))

    cb.assetDescription(issue.asset).flatMap(_.script.map(_.script)) should contain(forbiddingScript)
    cb.assetScript(issue.asset).map(_.script) should contain(forbiddingScript)
  }
}
