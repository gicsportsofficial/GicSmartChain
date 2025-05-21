package com.gicsports.lang.evaluator.tuple

import com.gicsports.lang.directives.values.{StdLibVersion, V4}
import com.gicsports.lang.evaluator.EvaluatorSpec
import com.gicsports.lang.v1.compiler.Terms.CONST_BOOLEAN
import com.gicsports.test._

class StrictWithTupleTest extends EvaluatorSpec {
  private implicit val version: StdLibVersion = V4

  property("strict with tuple") {
    eval(
      """
        | strict (str, i, cond, bytes) = ("12345", 12345, true, base58'')
        | str.parseInt() == i && cond && bytes.size() == 0
      """.stripMargin
    ) shouldBe Right(CONST_BOOLEAN(true))
  }

  property("each variable is strict") {
    eval(
      """
        | func f() = if (true) then throw("exception") else 4
        | let (a, b, c, d) = (1, 2, 3, f())
        | true
      """.stripMargin
    ) shouldBe Right(CONST_BOOLEAN(true))
    eval(
      """
        | func f() = if (true) then throw("exception") else 4
        | strict (a, b, c, d) = (1, 2, 3, f())
        | true
      """.stripMargin
    ) should produce("exception")
  }
}
