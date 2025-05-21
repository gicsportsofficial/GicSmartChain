package com.gicsports.lang.parser
import com.gicsports.lang.directives.values.{Expression, V6}
import com.gicsports.lang.utils.*
import com.gicsports.lang.v1.compiler.{Decompiler, TestCompiler}
import com.gicsports.test.PropSpec

class DeclarationSpacesTest extends PropSpec {
  property("absence of spaces between declaration syntax symbols should not be significant") {
    val script =
      """
        | func f(a:(Int,Int,Int),b:Int,c:Int)=[1,2,3]
        | let (a,b,c)=(1,2,3)
        | true
      """.stripMargin
    val compiled = TestCompiler(V6).compileExpression(script).expr
    Decompiler(compiled, getDecompilerContext(V6, Expression)) shouldBe
      """
        |func f (a,b,c) = [1, 2, 3]
        |
        |let $t04766 = $Tuple3(1, 2, 3)
        |let a = $t04766._1
        |let b = $t04766._2
        |let c = $t04766._3
        |true
      """.stripMargin.trim
  }

  property("big spaces between between tuple declarations should not be significant") {
    val script =
      """
        | let (
        |  a ,
        |  b ,
        |  c
        | ) =
        |  (
        |    1 ,
        |    2 ,
        |    3
        |  )
        | true
      """.stripMargin
    val compiled = TestCompiler(V6).compileExpression(script).expr
    Decompiler(compiled, getDecompilerContext(V6, Expression)) shouldBe
      """
        |let $t0258 = $Tuple3(1, 2, 3)
        |let a = $t0258._1
        |let b = $t0258._2
        |let c = $t0258._3
        |true
      """.stripMargin.trim
  }
}
