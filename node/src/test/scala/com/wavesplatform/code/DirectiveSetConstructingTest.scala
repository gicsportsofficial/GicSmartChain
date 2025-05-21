package com.gicsports.code

import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.lang.directives.values._
import com.gicsports.test._

class DirectiveSetConstructingTest extends PropSpec {
  property("DirectiveSet should be successfully constructed with (V3, Account, Contract) params") {
    DirectiveSet(V3, Account, DApp) shouldBe DirectiveSet(V3, Account, DApp)
  }

  property("DirectiveSet should be successfully constructed with (<any>, <any>, Expression) params") {
    DirectiveSet(V1, Account, Expression) shouldBe DirectiveSet(V1, Account, Expression)
    DirectiveSet(V2, Asset, Expression) shouldBe DirectiveSet(V2, Asset, Expression)
    DirectiveSet(V3, Asset, Expression) shouldBe DirectiveSet(V3, Asset, Expression)
  }

  property("DirectiveSet should not be constructed with wrong param set") {
    DirectiveSet(V1, Account, DApp) should produce("Inconsistent set of directives")
    DirectiveSet(V2, Account, DApp) should produce("Inconsistent set of directives")
    DirectiveSet(V3, Asset, DApp) should produce("Inconsistent set of directives")
  }
}
