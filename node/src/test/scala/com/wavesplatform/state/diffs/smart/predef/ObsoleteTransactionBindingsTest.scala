package com.gicsports.state.diffs.smart.predef

import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.db.WithState
import com.gicsports.lagonaki.mocks.TestBlock
import com.gicsports.lang.directives.values.{Expression, V1}
import com.gicsports.lang.script.v1.ExprScript
import com.gicsports.lang.utils._
import com.gicsports.lang.v1.compiler.ExpressionCompiler
import com.gicsports.lang.v1.parser.Parser
import com.gicsports.settings.{FunctionalitySettings, TestFunctionalitySettings}
import com.gicsports.state.diffs.ENOUGH_AMT
import com.gicsports.test.PropSpec
import com.gicsports.transaction.smart.SetScriptTransaction
import com.gicsports.transaction.transfer.TransferTransaction
import com.gicsports.transaction.{GenesisTransaction, PaymentTransaction, TxHelpers}

class ObsoleteTransactionBindingsTest extends PropSpec with WithState {

  def script(g: GenesisTransaction, p: PaymentTransaction): String =
    s"""let genTx = extract(transactionById(base58'${g.id().toString}'))
       |let payTx = extract(transactionById(base58'${p.id().toString}'))
       |
       |let genTotal = match genTx {
       |  case gen: GenesisTransaction =>
       |    let genId = gen.id == base58'${g.id().toString}'
       |    let genFee = gen.fee == ${g.fee}
       |    let genTimestamp = gen.timestamp== ${g.timestamp}
       |    let genVersion = gen.version == 1
       |    let genAmount = gen.amount == ${g.amount}
       |    let genRecipient = gen.recipient == Address(base58'${g.recipient}')
       |    genId && genFee && genTimestamp && genVersion && genAmount && genRecipient
       |   case _ => false
       | }
       |
       |let payTotal = match payTx {
       |  case pay: PaymentTransaction =>
       |    let payId = pay.id == base58'${p.id().toString}'
       |    let payFee = pay.fee == ${p.fee}
       |    let payTimestamp = pay.timestamp== ${p.timestamp}
       |    let payVersion = pay.version == 1
       |    let payAmount = pay.amount == ${p.amount}
       |    let payRecipient = pay.recipient == Address(base58'${p.recipient}')
       |
       |    let bodyBytes = pay.bodyBytes == base64'${ByteStr(p.bodyBytes.apply()).base64}'
       |    let sender = pay.sender == addressFromPublicKey(base58'${p.sender}')
       |    let senderPublicKey = pay.senderPublicKey == base58'${p.sender}'
       |    let signature = pay.proofs[0]== base58'${p.signature.toString}'
       |    let empty1 = pay.proofs[1]== base58''
       |    let empty2 = pay.proofs[2]== base58''
       |    let empty3 = pay.proofs[3]== base58''
       |    let empty4 = pay.proofs[4]== base58''
       |    let empty5 = pay.proofs[5]== base58''
       |    let empty6 = pay.proofs[6]== base58''
       |    let empty7 = pay.proofs[7]== base58''
       |
       |    let payBindings = payId && payFee && payTimestamp && payVersion && payAmount && payRecipient
       |    let payBindings1 = bodyBytes && sender && senderPublicKey && signature
       |    let payBindings2 = empty1 && empty2 && empty3 && empty4 && empty5 && empty6 && empty7
       |
       |    payBindings && payBindings1 && payBindings2
       |  case _ => false
       |}
       |
       |genTotal && payTotal
       |""".stripMargin

  val preconditionsAndPayments: Seq[(GenesisTransaction, PaymentTransaction, SetScriptTransaction, TransferTransaction)] = {
    val master     = TxHelpers.signer(1)
    val recipients = Seq(master, TxHelpers.signer(2))

    val genesis = TxHelpers.genesis(master.toAddress, ENOUGH_AMT * 3)
    recipients.map { recipient =>
      val payment       = TxHelpers.payment(master, recipient.toAddress, ENOUGH_AMT * 2)
      val untypedScript = Parser.parseExpr(script(genesis, payment)).get.value
      val typedScript = ExprScript(ExpressionCompiler(compilerContext(V1, Expression, isAssetScript = false), untypedScript).explicitGet()._1)
        .explicitGet()
      val setScriptTransaction = TxHelpers.setScript(recipient, typedScript)
      val nextTransfer         = TxHelpers.transfer(recipient, master.toAddress)
      (genesis, payment, setScriptTransaction, nextTransfer)
    }
  }

  val settings: FunctionalitySettings = TestFunctionalitySettings.Enabled.copy(blockVersion3AfterHeight = 100)
  property("Obsolete transaction bindings") {
    preconditionsAndPayments.foreach {
      case (genesis, payment, setScriptTransaction, nextTransfer) =>
        assertDiffAndState(Seq(TestBlock.create(Seq(genesis, payment, setScriptTransaction))), TestBlock.create(Seq(nextTransfer)), settings) {
          (_, _) =>
            ()
        }
    }
  }
}
