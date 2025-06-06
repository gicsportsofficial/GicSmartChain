package com.gicsports.lang.compiler
import com.gicsports.lang.directives.values.V6
import com.gicsports.lang.v1.compiler.TestCompiler
import com.gicsports.test.PropSpec

class FreeCallTest extends PropSpec {
  property("incorrect result type") {
    intercept[Exception](TestCompiler(V6).compileFreeCall("true")).getMessage shouldBe "Compilation failed: [" +
      "FreeCall needs to return " +
      "(List[BinaryEntry|BooleanEntry|Burn|DeleteEntry|IntegerEntry|Issue|Lease|LeaseCancel|Reissue|ScriptTransfer|SponsorFee|StringEntry], Any)" +
      "|" +
      "List[BinaryEntry|BooleanEntry|Burn|DeleteEntry|IntegerEntry|Issue|Lease|LeaseCancel|Reissue|ScriptTransfer|SponsorFee|StringEntry], but got 'Boolean' in 0-0" +
      "]"
  }
}
