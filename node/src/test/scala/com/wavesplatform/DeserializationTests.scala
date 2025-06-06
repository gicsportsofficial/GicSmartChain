package com.gicsports

import com.gicsports.serialization.Deser
import com.gicsports.test.FreeSpec

class DeserializationTests extends FreeSpec {

  "serializeArray" - {
    "works with arrays < 32k" in {
      val byteArray = Array.fill(Short.MaxValue)(0.toByte)
      Deser.serializeArrayWithLength(byteArray) should not be empty
    }
    "IllegalArgumentException thrown with arrays > 32k" in {
      val byteArray = Array.fill(Short.MaxValue + 1)(0.toByte)
      an[IllegalArgumentException] should be thrownBy Deser.serializeArrayWithLength(byteArray)
    }
  }
}
