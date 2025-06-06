package com.gicsports.lang.v1

import cats.implicits.*
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.gicsports.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import com.gicsports.lang.v1.repl.node.ErrorMessageEnvironment
import com.gicsports.lang.v1.repl.node.http.{NodeClient, NodeClientImpl, NodeConnectionSettings, WebEnvironment}
import com.gicsports.lang.v1.traits.Environment

import scala.concurrent.Future

package object repl {
  val global: BaseGlobal             = com.gicsports.lang.Global
  val internalVarPrefixes: Set[Char] = Set('@', '$')
  val internalFuncPrefix: String     = "_"

  val version                  = V6
  val directives: DirectiveSet = DirectiveSet(version, Account, DApp).explicitGet()

  val initialCtx: CTX[Environment] =
    CryptoContext.build(global, version).withEnvironment[Environment] |+|
      PureContext.build(version, useNewPowPrecision = true).withEnvironment[Environment] |+|
      WavesContext.build(global, directives, fixBigScriptField = true)

  def buildEnvironment(settings: Option[NodeConnectionSettings], customHttpClient: Option[NodeClient]): Environment[Future] =
    settings.fold(
      ErrorMessageEnvironment[Future]("Blockchain state is unavailable from REPL"): Environment[Future]
    )(s => WebEnvironment(s, customHttpClient.getOrElse(NodeClientImpl(s.normalizedUrl))))
}
