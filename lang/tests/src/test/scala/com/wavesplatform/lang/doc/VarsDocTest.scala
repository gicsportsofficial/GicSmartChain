package com.gicsports.lang.doc

import cats.syntax.semigroup.*
import com.gicsports.DocSource
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.Global
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.v1.CTX
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.gicsports.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.gicsports.lang.v1.traits.Environment
import com.gicsports.test.*

class VarsDocTest extends PropSpec {
  def buildFullContext(ds: DirectiveSet): CTX[Environment] = {
    val wavesCtx  = WavesContext.build(Global, ds, fixBigScriptField = true)
    val cryptoCtx = CryptoContext.build(Global, ds.stdLibVersion).withEnvironment[Environment]
    val pureCtx   = PureContext.build(ds.stdLibVersion, useNewPowPrecision = true).withEnvironment[Environment]
    pureCtx |+| cryptoCtx |+| wavesCtx
  }

  property("declared ride VARs have doc for all contexts") {
    val totalVarDocs = for {
      ds <- directives
      ctx = buildFullContext(ds)
      doc <- varsDoc(ctx, ds.stdLibVersion)
    } yield doc

    val varsWithoutDocInfo = totalVarDocs.filter(_._1.isEmpty).map(_._2).mkString(", ")
    varsWithoutDocInfo shouldBe ""
  }

  lazy val directives: Seq[DirectiveSet] =
    DirectiveSet.contractDirectiveSet +:
      Set(V1, V2, V3, V4)
        .map(DirectiveSet(_, Account, Expression).explicitGet())
        .toSeq

  def varsDoc(ctx: CTX[Environment], ver: StdLibVersion): Iterable[(Option[String], String)] =
    ctx.vars.keys
      .map(k => (DocSource.varData.get((k, ver.value.asInstanceOf[Int])), k))
}
