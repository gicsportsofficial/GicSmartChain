package com.gicsports.lang.evaluator

import com.gicsports.common.state.ByteStr
import com.gicsports.lang.v1.compiler.Terms
import com.gicsports.lang.v1.compiler.Terms.{CONST_BYTESTR, CONST_STRING}
import com.gicsports.test.PropSpec
import org.scalatest.Inside

class SmartConstructorTest extends PropSpec with Inside {
  property("CONST_BYTESTR size limit") {
    val allowedBytes = ByteStr.fill(Terms.DataEntryValueMax)(1)
    inside(CONST_BYTESTR(allowedBytes)) {
      case Right(CONST_BYTESTR(bytes)) => bytes shouldBe allowedBytes
    }

    val illegalBytes = ByteStr.fill(Terms.DataEntryValueMax + 1)(1)
    CONST_BYTESTR(illegalBytes) shouldBe Symbol("left")
  }

  property("CONST_STRING size limit") {
    val allowedString = "ё" * (Terms.DataEntryValueMax / 2)
    inside(CONST_STRING(allowedString)) {
      case Right(CONST_STRING(str)) =>
        str shouldBe allowedString
        str.getBytes("UTF-8").length shouldBe Terms.DataEntryValueMax - 1
    }

    val illegalString = "ё" * (Terms.DataEntryValueMax / 2 + 1)
    CONST_STRING(illegalString) shouldBe Symbol("left")
  }
}
