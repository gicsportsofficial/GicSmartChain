package com.gicsports.lang.estimator

import com.gicsports.lang.v1.estimator.v3.ScriptEstimatorV3

class TypeCastComplexityTest extends ScriptEstimatorTestBase(ScriptEstimatorV3(fixOverflow = true, overhead = false)) {
  property("type cast complexity") {
    estimate("1") shouldBe Right(0)
    estimate("1.as[Int]") shouldBe Right(1)
    estimate("1.exactAs[Int]") shouldBe Right(4)

    estimate("[1, 2, 3]") shouldBe Right(3)
    estimate("[1, 2, 3].as[List[Any]]") shouldBe Right(4)
    estimate("[1, 2, 3].exactAs[List[Any]]") shouldBe Right(7)
  }
}
