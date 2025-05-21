package com.gicsports.account

import com.gicsports.test.PropSpec

class AliasSpecification extends PropSpec {

  property("Correct alias should be valid") {
    forAll(validAliasStringGen) { s =>
      Alias.create(s) should beRight
    }
  }

  property("Incorrect alias should be invalid") {
    forAll(invalidAliasStringGen) { s =>
      Alias.create(s) should beLeft
    }
  }
}
