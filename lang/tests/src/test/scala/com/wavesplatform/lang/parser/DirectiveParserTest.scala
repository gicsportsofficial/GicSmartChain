package com.gicsports.lang.parser

import com.gicsports.lang.directives.DirectiveKey.*
import com.gicsports.lang.directives.values.*
import com.gicsports.lang.directives.{Directive, DirectiveParser}
import com.gicsports.test.*

class DirectiveParserTest extends PropSpec {

  def parse(s: String): Either[String, List[Directive]] = DirectiveParser(s)

  property("parse directives") {
    parse("{-# STDLIB_VERSION 1 #-}") shouldBe Right(List(Directive(STDLIB_VERSION, V1)))

    parse("""
        |
        |{-# STDLIB_VERSION 1 #-}
        |
      """.stripMargin) shouldBe Right(List(Directive(STDLIB_VERSION, V1)))

    parse("""
            |
            |{-# CONTENT_TYPE EXPRESSION #-}
            |
      """.stripMargin) shouldBe Right(List(Directive(CONTENT_TYPE, Expression)))

    parse("""
            |
            |{-# SCRIPT_TYPE ASSET #-}
            |
      """.stripMargin) shouldBe Right(List(Directive(SCRIPT_TYPE, Asset)))
  }

  property("parse directives with wrong key should produce error") {
    val wrongKey = "WRONG_DIRECTIVE_KEY"
    parse(s"""
            |
            |{-# $wrongKey VALUE #-}
            |
      """.stripMargin) shouldBe Left("Illegal directive key " + wrongKey)

    parse(s"""
            |
            |{-# STDLIB_VERSION 1 #-}
            |{-# CONTENT_TYPE EXPRESSION #-}
            |{-# $wrongKey VALUE #-}
            |
      """.stripMargin) shouldBe Left("Illegal directive key " + wrongKey)
  }

  property("parse directives with existing key and wrong value should produce error") {
    val wrongValue = "WRONG"
    parse(s"""
            |
            |{-# SCRIPT_TYPE $wrongValue #-}
            |
      """.stripMargin) shouldBe Left(s"Illegal directive value $wrongValue for key SCRIPT_TYPE")

    parse(s"""
            |
            |{-# STDLIB_VERSION 1 #-}
            |{-# CONTENT_TYPE EXPRESSION #-}
            |{-# SCRIPT_TYPE $wrongValue #-}
            |
      """.stripMargin) shouldBe Left(s"Illegal directive value $wrongValue for key SCRIPT_TYPE")
  }

  property("directive with illegal format") {
    parse("{-# ILLEGAL #-}") shouldBe Left("Directive {-# ILLEGAL #-} has illegal format")
    parse("{-# A%@#%$^$ A%#%%$ #-}") shouldBe Symbol("left")
    parse("{-# AAA BBB CCC DDD #-}") shouldBe Symbol("left")
  }

  property("directive duplicate") {
    parse(
      s"""
         | {-# STDLIB_VERSION 1 #-}
         | {-# CONTENT_TYPE EXPRESSION #-}
         | {-# CONTENT_TYPE EXPRESSION #-}
         | {-# SCRIPT_TYPE  ASSET #-}
      """.stripMargin
    ) shouldBe Left("Directive key CONTENT_TYPE is used more than once")
  }

  property("spaces between directives") {
    parse(
      s"""
         | {-# STDLIB_VERSION 3 #-}
         |
         |
         |    {-# CONTENT_TYPE EXPRESSION #-}
         |
         |
         | {-# SCRIPT_TYPE  ASSET #-}
      """.stripMargin
    ) shouldBe Right(List(
      Directive(STDLIB_VERSION, V3),
      Directive(CONTENT_TYPE, Expression),
      Directive(SCRIPT_TYPE, Asset)
    ))
  }

  property("dashes, spaces, dots, underscores") {
    parse("{-# IMPORT path/lib_1-a.ride, lib_2-b.ride #-}") shouldBe
      Right(List(Directive(IMPORT, Imports(List("path/lib_1-a.ride", "lib_2-b.ride")))))
  }
}
