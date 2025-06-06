package com.gicsports.transaction.smart.script

import com.gicsports.common.utils._
import com.gicsports.crypto
import com.gicsports.lang.directives.DirectiveDictionary
import com.gicsports.lang.directives.values._
import com.gicsports.lang.script.{ContractScript, ScriptReader}
import com.gicsports.lang.v1.estimator.v2.ScriptEstimatorV2
import com.gicsports.lang.v1.serialization.SerdeV1
import com.gicsports.lang.v1.testing.TypedScriptGen
import com.gicsports.test._
import com.gicsports.test.PropSpec
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{EitherValues, Inside}

class ScriptReaderTest extends PropSpec with TypedScriptGen with Inside with EitherValues {
  val checksumLength = 4

  property("should parse all bytes for V1") {
    forAll(exprGen) { sc =>
      val body     = Array(V1.id.toByte) ++ SerdeV1.serialize(sc) ++ "foo".getBytes("UTF-8")
      val allBytes = body ++ crypto.secureHash(body).take(checksumLength)
      ScriptReader.fromBytes(allBytes) should produce("bytes left")
    }
  }

  property("should parse all bytes for V3") {
    forAll(contractGen) { sc =>
      val allBytes = ContractScript.apply(V3, sc).explicitGet().bytes().arr
      ScriptReader.fromBytes(allBytes).explicitGet().expr shouldBe sc
    }
  }

  property("should parse all bytes for V6") {
    forAll(contractGen) { sc =>
      val allBytes = ContractScript.apply(V6, sc).explicitGet().bytes().arr
      ScriptReader.fromBytes(allBytes).explicitGet().expr shouldBe sc
    }
  }

  property("should parse expression with all supported std lib version") {
    val scriptEthList =
      DirectiveDictionary[StdLibVersion].all.map { version =>
        ScriptCompiler.compile(
          s"""
             |{-# STDLIB_VERSION ${version.value} #-}
             |  true
             """.stripMargin,
          ScriptEstimatorV2
        )
      }
    scriptEthList.foreach(_.explicitGet())

    scriptEthList.foreach { scriptEth =>
      ScriptReader.fromBytes(scriptEth.explicitGet()._1.bytes().arr).explicitGet()
    }
  }

  property("should return correct error for invalid length script") {
    import ScriptReaderTest._

    forAll(invalidPrefixV0Length) { scBytes =>
      ScriptReader.fromBytes(scBytes).left.value
    }
  }

  property("should return correct error for script with invalid start bytes") {
    import ScriptReaderTest._

    forAll(Gen.oneOf(invalidPrefixV0, invalidPrefix)) { scBytes =>
      ScriptReader.fromBytes(scBytes).left.value
    }
  }
}

object ScriptReaderTest {

  val validStdLibVersions: Set[Byte] = DirectiveDictionary[StdLibVersion].all.map(_.id.toByte).toSet
  val validContentTypes: Set[Byte]   = DirectiveDictionary[ContentType].all.map(_.id.toByte).toSet

  val invalidPrefixV0ContentType: Gen[Array[Byte]] =
    for {
      c <- Gen.negNum[Byte]
      v <- Gen.oneOf(validStdLibVersions.toSeq)
    } yield 0.toByte +: Array(c, v)

  val invalidPrefixV0StdLibVersion: Gen[Array[Byte]] =
    for {
      c <- Gen.oneOf(validContentTypes.toSeq)
      v <- Gen.negNum[Byte]
    } yield 0.toByte +: Array(c, v)

  val invalidPrefixV0Both: Gen[Array[Byte]] =
    for {
      c <- Gen.negNum[Byte]
      v <- Gen.negNum[Byte]
    } yield 0.toByte +: Array(c, v)

  // version byte 0 but no StdLibVersion byte and/or no ContentType byte
  val invalidPrefixV0Length: Gen[Array[Byte]] =
    for {
      n  <- Gen.oneOf(0, 1)
      bs <- Gen.listOfN(n, Arbitrary.arbitrary[Byte])
    } yield 0.toByte +: bs.toArray

  // version byte 0 but unknown StdLibVersion byte and/or unknown ContentType byte
  val invalidPrefixV0: Gen[Array[Byte]] =
    Gen.oneOf(invalidPrefixV0ContentType, invalidPrefixV0StdLibVersion, invalidPrefixV0Both)

  // invalid version byte and unknown length of remaining bytes
  val invalidPrefix: Gen[Array[Byte]] =
    for {
      v  <- Gen.negNum[Byte]
      n  <- Gen.choose(0, 5)
      bs <- Gen.listOfN(n, Arbitrary.arbitrary[Byte])
    } yield v +: bs.toArray
}
