package com.gicsports.transaction

import com.gicsports.account.{KeyPair, PublicKey}
import com.gicsports.common.utils.{Base58, EitherExt2}
import com.gicsports.protobuf.transaction.PBTransactions
import com.gicsports.test.PropSpec
import org.scalacheck.{Arbitrary, Gen}

class GenesisTransactionSpecification extends PropSpec {

  private val defaultRecipient = PublicKey(Array.fill(32)(0: Byte))

  property("GenesisTransaction Signature should be the same") {
    val balance   = 457L
    val timestamp = 2398762345L
    val signature = GenesisTransaction.generateSignature(defaultRecipient.toAddress, balance, timestamp)

    val expected = "3L4zhpN1o6TysvM8FZFv1NmSEjpGSgV4V71e2iJwseFrrt65aZJiyXwqj5WpigLAn296sUrFb9yN8fdsY7GSdwwR"
    val actual   = Base58.encode(signature)

    assert(actual == expected)
  }

  property("GenesisTransaction parse from Bytes should work fine") {
    val bytes = Base58.tryDecodeWithLimit("5GoidXWjBfzuS9tZm4Fp6GAXUYFunVMsoWAew3eBnDbmaDi7WiP9yVpBD2").get

    val actualTransaction = GenesisTransaction.parseBytes(bytes).get

    val balance             = 12345L
    val timestamp           = 1234567890L
    val expectedTransaction = GenesisTransaction.create(defaultRecipient.toAddress, balance, timestamp).explicitGet()

    actualTransaction should equal(expectedTransaction)
  }

  property("GenesisTransaction serialize/deserialize roundtrip") {
    forAll(Gen.listOfN(32, Arbitrary.arbitrary[Byte]).map(_.toArray), Gen.posNum[Long], Gen.posNum[Long]) {
      (recipientSeed: Array[Byte], time: Long, amount: Long) =>
        val recipient = KeyPair(recipientSeed)
        val source    = GenesisTransaction.create(recipient.toAddress, amount, time).explicitGet()
        val bytes     = source.bytes()
        val dest      = GenesisTransaction.parseBytes(bytes).get
        source should equal(dest)

        val proto           = PBTransactions.protobuf(source)
        val fromProto       = PBTransactions.vanilla(proto, unsafe = false).explicitGet()
        val fromProtoUnsafe = PBTransactions.vanillaUnsafe(proto)
        fromProto shouldBe source
        fromProtoUnsafe shouldBe source
    }
  }

}
