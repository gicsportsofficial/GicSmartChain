package com.gicsports.lang

import cats.syntax.semigroup.*
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.V3
import com.gicsports.lang.v1.FunctionHeader
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.lang.v1.compiler.Terms.EXPR
import com.gicsports.lang.v1.estimator.ScriptEstimator
import com.gicsports.lang.v1.evaluator.ContractEvaluator.LogExtraInfo
import com.gicsports.lang.v1.evaluator.EvaluatorV2
import com.gicsports.lang.v1.evaluator.ctx.impl.PureContext
import com.gicsports.lang.v1.evaluator.ctx.impl.waves.WavesContext
import com.gicsports.lang.v1.traits.Environment
import monix.eval.Coeval

package object estimator {
  private val ctx =
    PureContext.build(V3, useNewPowPrecision = true).withEnvironment[Environment] |+|
      WavesContext.build(Global, DirectiveSet.contractDirectiveSet, fixBigScriptField = true)

  private val environment = Common.emptyBlockchainEnvironment()
  private def evaluator(overhead: Boolean, expr: EXPR) =
    EvaluatorV2.applyCompleted(ctx.evaluationContext(environment), expr, LogExtraInfo(), V3, correctFunctionCallScope = true, overhead)

  def evaluatorV2AsEstimator(overhead: Boolean): ScriptEstimator = new ScriptEstimator {
    override val version: Int = 0

    override def apply(declaredVals: Set[String], functionCosts: Map[FunctionHeader, Coeval[Long]], expr: Terms.EXPR): Either[String, Long] =
      Right(evaluator(overhead, expr)._2)
  }
}
