package com.gicsports.state.diffs.invoke

import cats.syntax.semigroup.*
import com.gicsports.common.utils.EitherExt2
import com.gicsports.features.BlockchainFeatures.{ConsensusImprovements, SynchronousCalls}
import com.gicsports.lang.Global
import com.gicsports.lang.directives.values.{Account, DApp, StdLibVersion, V3}
import com.gicsports.lang.directives.{DirectiveDictionary, DirectiveSet}
import com.gicsports.lang.v1.evaluator.ctx.InvariableContext
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.gicsports.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.gicsports.lang.v1.traits.Environment
import com.gicsports.state.Blockchain

object CachedDAppCTX {
  private val cache: Map[(StdLibVersion, Boolean, Boolean), InvariableContext] =
    (for {
      version            <- DirectiveDictionary[StdLibVersion].all.filter(_ >= V3)
      useNewPowPrecision <- Seq(true, false)
      fixBigScriptField  <- Seq(true, false)
    } yield {
      val ctx = PureContext.build(version, useNewPowPrecision).withEnvironment[Environment] |+|
        CryptoContext.build(Global, version).withEnvironment[Environment] |+|
        WavesContext.build(Global, DirectiveSet(version, Account, DApp).explicitGet(), fixBigScriptField)
      ((version, useNewPowPrecision, fixBigScriptField), InvariableContext(ctx))
    }).toMap

  def get(version: StdLibVersion, b: Blockchain): InvariableContext =
    cache(
      (
        version,
        b.isFeatureActivated(SynchronousCalls) && b.height > b.settings.functionalitySettings.enforceTransferValidationAfter,
        b.isFeatureActivated(ConsensusImprovements)
      )
    )
}
