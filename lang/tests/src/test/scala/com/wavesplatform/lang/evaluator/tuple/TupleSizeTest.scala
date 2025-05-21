package com.gicsports.lang.evaluator.tuple

import com.gicsports.lang.directives.values.{StdLibVersion, V6}
import com.gicsports.lang.evaluator.EvaluatorSpec
import com.gicsports.lang.v1.ContractLimits
import com.gicsports.lang.v1.compiler.Terms.CONST_LONG

class TupleSizeTest extends EvaluatorSpec {
  implicit val startVersion: StdLibVersion = V6

  property("tuple size") {
    (ContractLimits.MinTupleSize to ContractLimits.MaxTupleSize)
      .map(1 to)
      .foreach(range => eval(s"(${range.mkString(",")}).size()") shouldBe Right(CONST_LONG(range.length)))
  }
}
