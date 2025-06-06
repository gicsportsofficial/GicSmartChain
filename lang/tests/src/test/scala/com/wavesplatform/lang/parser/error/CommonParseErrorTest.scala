package com.gicsports.lang.parser.error

import com.gicsports.lang.directives.values.V6
import com.gicsports.lang.v1.compiler.TestCompiler

class CommonParseErrorTest extends ParseErrorTest {
  property("empty script as expression") {
    TestCompiler(V6).compileExpressionE("") shouldBe Left("Parse error: expected result expression in 0-0")
  }

  property("empty script with comment as expression") {
    TestCompiler(V6).compileExpressionE("#abcd") shouldBe Left("Parse error: expected result expression in 5-5")
  }

  property("illegal line break inside comment") {
    assert(
      """
        | # comment
        | # comment
        | break
        | # comment
        | # comment
        | func(a: Int) = a
      """.stripMargin,
      """Parse error: illegal expression""",
      24,
      29,
      "break",
      onlyDApp = true
    )
  }

  property("cyrillic charset for definition") {
    assert(
      """
        | func кириллица() = []
      """.stripMargin,
      """Parse error: expected only latin charset for definitions""",
      7,
      18,
      "кириллица()"
    )
  }

  property("chinese charset for definition") {
    assert(
      """
        | let 煊镕不 = []
      """.stripMargin,
      """Parse error: expected only latin charset for definitions""",
      6,
      9,
      "煊镕不"
    )
  }
}
