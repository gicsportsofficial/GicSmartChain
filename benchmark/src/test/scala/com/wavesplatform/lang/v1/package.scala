package com.gicsports.lang

import cats.Id
import com.gicsports.lang.directives.values.StdLibVersion
import com.gicsports.lang.v1.FunctionHeader.Native
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.lang.v1.compiler.Terms.{CONST_BIGINT, CONST_LONG, EXPR, FUNCTION_CALL}
import com.gicsports.lang.v1.evaluator.ContractEvaluator.LogExtraInfo
import com.gicsports.lang.v1.evaluator.FunctionIds.POW_BIGINT
import com.gicsports.lang.v1.evaluator.ctx.EvaluationContext
import com.gicsports.lang.v1.evaluator.ctx.impl.Rounding
import com.gicsports.lang.v1.evaluator.{EvaluatorV2, Log}
import com.gicsports.lang.v1.traits.Environment

package object v1 {
  def pow(base: BigInt, basePrecision: Int, exponent: BigInt, exponentPrecision: Int, resultPrecision: Int): EXPR =
    FUNCTION_CALL(
      Native(POW_BIGINT),
      List(
        CONST_BIGINT(base),
        CONST_LONG(basePrecision),
        CONST_BIGINT(exponent),
        CONST_LONG(exponentPrecision),
        CONST_LONG(resultPrecision),
        Rounding.Down.value
      )
    )

  def eval(
      ctx: EvaluationContext[Environment, Id],
      expr: EXPR,
      stdLibVersion: StdLibVersion
  ): (Log[Id], Int, Either[ExecutionError, Terms.EVALUATED]) =
    EvaluatorV2.applyCompleted(ctx, expr, LogExtraInfo(), stdLibVersion, newMode = true, correctFunctionCallScope = true)
}
