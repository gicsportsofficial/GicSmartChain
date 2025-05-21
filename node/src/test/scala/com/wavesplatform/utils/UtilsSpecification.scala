package com.gicsports.utils

import cats.Id
import com.gicsports.common.state.ByteStr
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values.V3
import com.gicsports.lang.utils._
import com.gicsports.lang.v1.compiler.Terms.{FUNCTION_CALL, TRUE}
import com.gicsports.lang.v1.compiler.Types.BOOLEAN
import com.gicsports.lang.v1.evaluator.ctx.{EvaluationContext, UserFunction}
import com.gicsports.lang.v1.traits.Environment
import com.gicsports.state.diffs.smart.predef.chainId
import com.gicsports.test.FreeSpec
import com.gicsports.transaction.smart.WavesEnvironment
import monix.eval.Coeval

class UtilsSpecification extends FreeSpec {
  private val environment = new WavesEnvironment(chainId, Coeval(???), null, EmptyBlockchain, null, DirectiveSet.contractDirectiveSet, ByteStr.empty)

  "estimate()" - {
    "handles functions that depend on each other" in {
      val callee = UserFunction[Environment]("callee", 0, BOOLEAN)(TRUE)
      val caller = UserFunction[Environment]("caller", 0, BOOLEAN)(FUNCTION_CALL(callee.header, List.empty))
      val ctx = EvaluationContext.build[Id, Environment](
        environment,
        typeDefs = Map.empty,
        letDefs = Map.empty,
        functions = Seq(caller, callee)
      )
      estimate(V3, ctx).size shouldBe 2
    }
  }
}
