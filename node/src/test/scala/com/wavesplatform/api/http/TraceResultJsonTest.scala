package com.gicsports.api.http

import com.gicsports.account.{Address, PublicKey}
import com.gicsports.api.http.ApiError.ScriptExecutionError
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.lang.v1.FunctionHeader.User
import com.gicsports.lang.v1.compiler.Terms.{CONST_LONG, CONST_STRING, FUNCTION_CALL}
import com.gicsports.lang.v1.evaluator.ScriptResultV3
import com.gicsports.lang.v1.traits.domain.DataItem.Lng
import com.gicsports.lang.v1.traits.domain.{AssetTransfer, Recipient}
import com.gicsports.test.PropSpec
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.smart.InvokeScriptTransaction
import com.gicsports.transaction.smart.script.trace.{InvokeScriptTrace, TracedResult}
import com.gicsports.transaction.{Proofs, TxValidationError}
import com.gicsports.utils.JsonMatchers

class TraceResultJsonTest extends PropSpec with JsonMatchers {
  private val tx = (
    for {
      publicKey <- PublicKey.fromBase58String("9utotH1484Hb1WdAHuAKLjuGAmocPZg7jZDtnc35MuqT")
      address   <- Address.fromString("3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU")
      proof = ByteStr.decodeBase58("4scXzk4WiKMXG8p7V6J2pmznNZCgMjADbbZPSDGg28YLMKgshBmNFNzgYg2TwfKN3wMtgLiNQB77iQQZkH3roUyJ").get
      tx <- InvokeScriptTransaction.create(
        1.toByte,
        sender = publicKey,
        dappAddress = address,
        fc = Some(FUNCTION_CALL(User("func"), List(CONST_STRING("param").explicitGet(), CONST_LONG(1)))),
        p = List(InvokeScriptTransaction.Payment(1L, Waves)),
        fee = 10000000L,
        feeAssetId = Waves,
        timestamp = 1111L,
        proofs = Proofs(List(proof)),
        address.chainId
      )
    } yield tx
  ).explicitGet()

  property("suitable TracedResult json") {
    val vars = List(
      "amount"     -> Right(CONST_LONG(12345)),
      "invocation" -> CONST_STRING("str")
    )
    val recipient = Recipient.Address(ByteStr(tx.dApp.bytes))
    val trace = List(
      InvokeScriptTrace(
        tx.id(),
        tx.dApp,
        tx.funcCall,
        Right(
          ScriptResultV3(
            List(Lng("3FVV4W61poEVXEbFfPG1qfJhJxJ7Pk4M2To", 700000000)),
            List(AssetTransfer(recipient, recipient, 1, None)),
            0
          )
        ),
        vars,
        Nil
      )
    )

    val result = TracedResult(Right(tx), trace)
    result.json should matchJson("""{
                                      |  "type": 16,
                                      |  "id": "2hoMeTHAneLExjFo2a9ei7D4co5zzr9VyT7tmBmAGmeu",
                                      |  "sender": "3MvtiFpnSA7uYKXV3myLwRK3u2NEV91iJYW",
                                      |  "senderPublicKey": "9utotH1484Hb1WdAHuAKLjuGAmocPZg7jZDtnc35MuqT",
                                      |  "fee": 10000000,
                                      |  "feeAssetId": null,
                                      |  "timestamp": 1111,
                                      |  "proofs": [
                                      |    "4scXzk4WiKMXG8p7V6J2pmznNZCgMjADbbZPSDGg28YLMKgshBmNFNzgYg2TwfKN3wMtgLiNQB77iQQZkH3roUyJ"
                                      |  ],
                                      |  "version": 1,
                                      |  "dApp": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
                                      |  "payment": [
                                      |    {
                                      |      "amount": 1,
                                      |      "assetId": null
                                      |    }
                                      |  ],
                                      |  "call": {
                                      |    "function": "func",
                                      |    "args": [
                                      |      {
                                      |        "type": "string",
                                      |        "value": "param"
                                      |      },
                                      |      {
                                      |        "type": "integer",
                                      |        "value": 1
                                      |      }
                                      |    ]
                                      |  },
                                      |  "trace": [
                                      |    {
                                      |      "type": "dApp",
                                      |      "id": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
                                      |      "function": "func",
                                      |      "args": [
                                      |        "param",
                                      |        "1"
                                      |      ],
                                      |      "invocations": [],
                                      |      "result": {
                                      |        "data": [
                                      |          {
                                      |            "key": "3FVV4W61poEVXEbFfPG1qfJhJxJ7Pk4M2To",
                                      |            "type": "integer",
                                      |            "value": 700000000
                                      |          }
                                      |        ],
                                      |        "transfers": [
                                      |          {
                                      |            "address": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
                                      |            "asset": null,
                                      |            "amount": 1
                                      |          }
                                      |        ],
                                      |        "issues": [],
                                      |        "reissues": [],
                                      |        "burns": [],
                                      |        "sponsorFees": [],
                                      |        "leases" : [],
                                      |        "leaseCancels" : [],
                                      |        "invokes": []
                                      |      },
                                      |      "error": null
                                      |    }
                                      |  ]
                                      |}""".stripMargin)

    result.loggedJson should matchJson(
      """{
        |  "type": 16,
        |  "id": "2hoMeTHAneLExjFo2a9ei7D4co5zzr9VyT7tmBmAGmeu",
        |  "sender": "3MvtiFpnSA7uYKXV3myLwRK3u2NEV91iJYW",
        |  "senderPublicKey": "9utotH1484Hb1WdAHuAKLjuGAmocPZg7jZDtnc35MuqT",
        |  "fee": 10000000,
        |  "feeAssetId": null,
        |  "timestamp": 1111,
        |  "proofs": [
        |    "4scXzk4WiKMXG8p7V6J2pmznNZCgMjADbbZPSDGg28YLMKgshBmNFNzgYg2TwfKN3wMtgLiNQB77iQQZkH3roUyJ"
        |  ],
        |  "version": 1,
        |  "dApp": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
        |  "payment": [
        |    {
        |      "amount": 1,
        |      "assetId": null
        |    }
        |  ],
        |  "call": {
        |    "function": "func",
        |    "args": [
        |      {
        |        "type": "string",
        |        "value": "param"
        |      },
        |      {
        |        "type": "integer",
        |        "value": 1
        |      }
        |    ]
        |  },
        |  "trace": [
        |    {
        |      "type": "dApp",
        |      "id": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
        |      "function": "func",
        |      "args": [
        |        "param",
        |        "1"
        |      ],
        |      "invocations": [],
        |      "result": {
        |        "data": [
        |          {
        |            "key": "3FVV4W61poEVXEbFfPG1qfJhJxJ7Pk4M2To",
        |            "type": "integer",
        |            "value": 700000000
        |          }
        |        ],
        |        "transfers": [
        |          {
        |            "address": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
        |            "asset": null,
        |            "amount": 1
        |          }
        |        ],
        |        "issues": [],
        |        "reissues": [],
        |        "burns": [],
        |        "sponsorFees": [],
        |        "leases" : [],
        |        "leaseCancels" : [],
        |        "invokes": []
        |      },
        |      "error": null,
        |      "vars": [
        |        {
        |          "name": "amount",
        |          "type": "Int",
        |          "value": 12345
        |        },
        |        {
        |          "name": "invocation",
        |          "type": "String",
        |          "value": "str"
        |        }
        |      ]
        |    }
        |  ]
        |}""".stripMargin
    )
  }

  property("suitable TracedResult error json") {
    val vars = List(
      "amount"     -> Right(CONST_LONG(12345)),
      "invocation" -> CONST_STRING("str")
    )
    val reason = "error reason"

    val trace = List(
      InvokeScriptTrace(
        tx.id(),
        tx.dApp,
        tx.funcCall,
        Left(TxValidationError.ScriptExecutionError(reason, vars, None)),
        vars,
        Nil
      )
    )
    val scriptExecutionError = ScriptExecutionError(tx, reason, isTokenScript = false)

    val result = TracedResult(Left(scriptExecutionError), trace)
    result.json should matchJson("""{
                                      |  "error": 306,
                                      |  "message": "Error while executing account-script: error reason",
                                      |  "transaction": {
                                      |    "type": 16,
                                      |    "id": "2hoMeTHAneLExjFo2a9ei7D4co5zzr9VyT7tmBmAGmeu",
                                      |    "sender": "3MvtiFpnSA7uYKXV3myLwRK3u2NEV91iJYW",
                                      |    "senderPublicKey": "9utotH1484Hb1WdAHuAKLjuGAmocPZg7jZDtnc35MuqT",
                                      |    "fee": 10000000,
                                      |    "feeAssetId": null,
                                      |    "timestamp": 1111,
                                      |    "proofs": [
                                      |      "4scXzk4WiKMXG8p7V6J2pmznNZCgMjADbbZPSDGg28YLMKgshBmNFNzgYg2TwfKN3wMtgLiNQB77iQQZkH3roUyJ"
                                      |    ],
                                      |    "version": 1,
                                      |    "dApp": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
                                      |    "payment": [
                                      |      {
                                      |        "amount": 1,
                                      |        "assetId": null
                                      |      }
                                      |    ],
                                      |    "call": {
                                      |      "function": "func",
                                      |      "args": [
                                      |        {
                                      |          "type": "string",
                                      |          "value": "param"
                                      |        },
                                      |        {
                                      |          "type": "integer",
                                      |          "value": 1
                                      |        }
                                      |      ]
                                      |    }
                                      |  },
                                      |  "trace": [
                                      |    {
                                      |      "type": "dApp",
                                      |      "id": "3MydsP4UeQdGwBq7yDbMvf9MzfB2pxFoUKU",
                                      |      "function": "func",
                                      |      "args": [
                                      |        "param",
                                      |        "1"
                                      |      ],
                                      |      "invocations": [],
                                      |      "result": "failure",
                                      |      "vars": [
                                      |        {
                                      |          "name": "amount",
                                      |          "type": "Int",
                                      |          "value": 12345
                                      |        },
                                      |        {
                                      |          "name": "invocation",
                                      |          "type": "String",
                                      |          "value": "str"
                                      |        }
                                      |      ],
                                      |      "error": "error reason"
                                      |    }
                                      |  ]
                                      |}""".stripMargin)
  }
}
