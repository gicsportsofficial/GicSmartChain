package com.gicsports.lang.evaluator

import cats.Id
import cats.kernel.Monoid
import com.gicsports.lang.Common.*
import com.gicsports.lang.Testing.*
import com.gicsports.lang.directives.values.V1
import com.gicsports.lang.v1.compiler.Terms.*
import com.gicsports.lang.v1.evaluator.Contextful.NoContext
import com.gicsports.lang.v1.evaluator.ctx.*
import com.gicsports.lang.v1.evaluator.ctx.EvaluationContext.*
import com.gicsports.lang.v1.evaluator.ctx.impl.PureContext
import com.gicsports.lang.v1.evaluator.ctx.impl.PureContext.*
import com.gicsports.test.PropSpec

class EvaluatorV1CaseObjField extends PropSpec {

  def context(p: CaseObj): EvaluationContext[NoContext, Id] =
    Monoid.combine(PureContext.build(V1, useNewPowPrecision = true).evaluationContext, sampleUnionContext(p))

  property("case custom type field access") {
    ev[CONST_LONG](
      context = context(pointAInstance),
      expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), "X"), CONST_LONG(2L)))
    ) shouldBe evaluated(5)
  }

  property("case custom type field access over union") {
    def testAccess(instance: CaseObj, field: String) =
      ev[CONST_LONG](
        context = context(instance),
        expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), field), CONST_LONG(2L)))
      )

    testAccess(pointAInstance, "X") shouldBe evaluated(5)
    testAccess(pointBInstance, "X") shouldBe evaluated(5)
    testAccess(pointAInstance, "YA") shouldBe evaluated(42)
    testAccess(pointBInstance, "YB") shouldBe evaluated(43)
  }
}
