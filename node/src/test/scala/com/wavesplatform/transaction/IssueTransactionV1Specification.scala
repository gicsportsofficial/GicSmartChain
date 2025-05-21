package com.gicsports.transaction
import com.google.protobuf.ByteString
import com.gicsports.account.{AddressScheme, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.{Base64, EitherExt2}
import com.gicsports.transaction.assets.IssueTransaction
import com.gicsports.crypto
import com.gicsports.test.PropSpec
import com.gicsports.transaction.serialization.impl.IssueTxSerializer
import play.api.libs.json.Json

class IssueTransactionV1Specification extends PropSpec {
  property("Issue serialization roundtrip") {
    forAll(issueGen) { issue: IssueTransaction =>
      val recovered = IssueTxSerializer.parseBytes(issue.bytes()).get
      recovered.bytes() shouldEqual issue.bytes()
    }
  }

  property("IssueV1 decode pre-encoded bytes") {
    val bytes = Base64.decode(
      "AziyOphUmQ/ePc6FSqkbePEgzTZ06Fjvr1LJrQnXose8B+UUJDsGEEL0BIUFv35jQprK5m1tiDLe0ho5wrz1vYED1SiqvsNcoQDYfHt6EoYy+vGc1EUxgZRXRFEToyoh7yIACEdpZ2Fjb2luAAhHaWdhY29pbgAAAAJUC+QACAEAAAAABfXhAAAAAWNd0/Qd"
    )
    val json = Json.parse("""{
                       "type": 3,
                       "id": "9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz",
                       "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
                       "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
                       "fee": 100000000,
                       "feeAssetId": null,
                       "timestamp": 1526287561757,
                       "version": 1,
                       "signature": "28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ",
                       "proofs": ["28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ"],
                       "assetId": "9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz",
                       "name": "Gigacoin",
                       "quantity": 10000000000,
                       "reissuable": true,
                       "decimals": 8,
                       "description": "Gigacoin"
                       }
    """)

    val tx = IssueTxSerializer.parseBytes(bytes).get
    tx.json() shouldBe json
    assert(crypto.verify(tx.signature, tx.bodyBytes(), tx.sender), "signature should be valid")
  }

  property("Issue serialization from TypedTransaction") {
    forAll(issueGen) { issue: IssueTransaction =>
      val recovered = TransactionParsers.parseBytes(issue.bytes()).get
      recovered.bytes() shouldEqual issue.bytes()
    }
  }

  property("JSON format validation") {
    val js = Json.parse("""{
                       "type": 3,
                       "id": "9KTBCwwkRBtkpsUvmzQTPLHsxCjdg6oxFCFwWnQiGpsU",
                       "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
                       "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
                       "fee": 100000000000,
                       "feeAssetId": null,
                       "timestamp": 1526287561757,
                       "version": 1,
                       "signature": "28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ",
                       "proofs": ["28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ"],
                       "assetId": "9KTBCwwkRBtkpsUvmzQTPLHsxCjdg6oxFCFwWnQiGpsU",
                       "name": "Gigacoin",
                       "quantity": 10000000000,
                       "reissuable": true,
                       "decimals": 8,
                       "description": "Gigacoin"
                       }
    """)

    val tx = IssueTransaction(
      TxVersion.V1,
      PublicKey.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
      ByteString.copyFromUtf8("Gigacoin"),
      ByteString.copyFromUtf8("Gigacoin"),
      TxPositiveAmount.unsafeFrom(10000000000L),
      TxDecimals.unsafeFrom(8.toByte),
      true,
      script = None,
      TxPositiveAmount.unsafeFrom(100000000000L),
      1526287561757L,
      Proofs(ByteStr.decodeBase58("28kE1uN1pX2bwhzr9UHw5UuB9meTFEDFgeunNgy6nZWpHX4pzkGYotu8DhQ88AdqUG6Yy5wcXgHseKPBUygSgRMJ").get),
      AddressScheme.current.chainId
    )

    tx.json() shouldEqual js
  }

}
