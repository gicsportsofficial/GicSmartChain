package com.gicsports.transaction.smart.script.estimator

import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.contract.DApp
import com.gicsports.lang.contract.DApp._
import com.gicsports.lang.directives.values._
import com.gicsports.lang.script.ContractScript
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.lang.v1.compiler.Terms._
import com.gicsports.lang.v1.estimator.ScriptEstimator
import com.gicsports.lang.v1.evaluator.ctx.impl.PureContext._
import com.gicsports.protobuf.dapp.DAppMeta
import com.gicsports.test.PropSpec

class ContractScriptComplexityTest(estimator: ScriptEstimator) extends PropSpec {

  property("estimate contract script correctly") {
    val contract = DApp(
      DAppMeta(),
      List.empty,
      List(
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "first",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              REF("x")
            )
          )
        ),
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "default",
            List(),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              REF("x")
            )
          )
        )
      ),
      Some(
        VerifierFunction(
          VerifierAnnotation(""),
          Terms.FUNC(
            "third",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              LET_BLOCK(
                LET("y", FUNCTION_CALL(sumLong.header, List(REF("x"), CONST_LONG(1)))),
                REF("y")
              )
            )
          )
        )
      )
    )

    ContractScript.estimateComplexity(V3, contract, estimator, true) shouldBe Right((41, Map("first" -> 32, "default" -> 20, "third" -> 41)))
  }

  property("estimate contract script with context correctly") {
    val contract = DApp(
      DAppMeta(),
      List(
        LET("y", FUNCTION_CALL(sumString.header, List(CONST_STRING("a").explicitGet(), CONST_STRING("b").explicitGet()))),
        LET("z", FUNCTION_CALL(sumString.header, List(CONST_STRING("c").explicitGet(), CONST_STRING("d").explicitGet())))
      ),
      List(
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "first",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(REF("y"), REF("z")))),
              REF("x")
            )
          )
        ),
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "default",
            List(),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              REF("x")
            )
          )
        )
      ),
      Some(
        VerifierFunction(
          VerifierAnnotation(""),
          Terms.FUNC(
            "third",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              LET_BLOCK(
                LET("y", FUNCTION_CALL(sumLong.header, List(REF("x"), CONST_LONG(1)))),
                REF("y")
              )
            )
          )
        )
      )
    )

    ContractScript.estimateComplexity(V3, contract, estimator, true) shouldBe Right((68, Map("first" -> 68, "default" -> 30, "third" -> 51)))
  }

  property("estimate contract script with context correctly 2") {
    val contract = DApp(
      DAppMeta(),
      List(
        LET("y", FUNCTION_CALL(sumString.header, List(CONST_STRING("a").explicitGet(), CONST_STRING("b").explicitGet()))),
        LET("z", FUNCTION_CALL(sumString.header, List(CONST_STRING("c").explicitGet(), CONST_STRING("d").explicitGet())))
      ),
      List(
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "first",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(REF("y"), REF("z")))),
              REF("x")
            )
          )
        ),
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "default",
            List(),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              REF("x")
            )
          )
        )
      ),
      Some(
        VerifierFunction(
          VerifierAnnotation(""),
          Terms.FUNC(
            "second",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              LET_BLOCK(
                LET("y", FUNCTION_CALL(sumLong.header, List(REF("x"), CONST_LONG(1)))),
                REF("y")
              )
            )
          )
        )
      )
    )

    ContractScript.estimateComplexity(V3, contract, estimator, true) shouldBe Right((68, Map("first" -> 68, "default" -> 30, "second" -> 51)))
  }

  property("estimate contract script with user functions") {
    val contract = DApp(
      DAppMeta(),
      List(
        LET("y", FUNCTION_CALL(sumString.header, List(CONST_STRING("a").explicitGet(), CONST_STRING("b").explicitGet()))),
        LET("z", FUNCTION_CALL(sumString.header, List(CONST_STRING("c").explicitGet(), CONST_STRING("d").explicitGet()))),
        Terms.FUNC(
          "g",
          List("arg1", "arg2"),
          LET_BLOCK(
            LET("x", FUNCTION_CALL(sumString.header, List(REF("y"), REF("z")))),
            REF("x")
          )
        ),
        LET("w", CONST_LONG(1)),
        Terms.FUNC(
          "f",
          List(),
          LET_BLOCK(
            LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), REF("w")))),
            REF("x")
          )
        )
      ),
      List(
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "first",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(REF("y"), REF("z")))),
              REF("x")
            )
          )
        ),
        CallableFunction(
          CallableAnnotation(""),
          Terms.FUNC(
            "default",
            List(),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              REF("x")
            )
          )
        )
      ),
      Some(
        VerifierFunction(
          VerifierAnnotation(""),
          Terms.FUNC(
            "second",
            List("arg1", "arg2"),
            LET_BLOCK(
              LET("x", FUNCTION_CALL(sumLong.header, List(CONST_LONG(3), CONST_LONG(0)))),
              LET_BLOCK(
                LET("y", FUNCTION_CALL(sumLong.header, List(REF("x"), CONST_LONG(1)))),
                REF("y")
              )
            )
          )
        )
      )
    )

    ContractScript.estimateUserFunctions(V3, contract, estimator) shouldBe Right(List("g"   -> 82, "f" -> 37))
    ContractScript.estimateGlobalVariables(V3, contract, estimator) shouldBe Right(List("y" -> 39, "z" -> 39, "w" -> 28))
  }
}
