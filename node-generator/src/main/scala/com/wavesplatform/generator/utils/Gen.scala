package com.gicsports.generator.utils

import java.util.concurrent.ThreadLocalRandom

import com.gicsports.account.{Address, KeyPair, PublicKey}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.crypto.Curve25519.KeyLength
import com.gicsports.generator.utils.Implicits._
import com.gicsports.lang.script.Script
import com.gicsports.lang.v1.estimator.ScriptEstimator
import com.gicsports.state.{BinaryDataEntry, BooleanDataEntry, DataEntry, EmptyDataEntry, IntegerDataEntry, StringDataEntry}
import com.gicsports.transaction.Asset.Waves
import com.gicsports.transaction.Transaction
import com.gicsports.transaction.smart.script.ScriptCompiler
import com.gicsports.transaction.transfer.MassTransferTransaction.ParsedTransfer
import com.gicsports.transaction.transfer._
import com.gicsports.utils.LoggerFacade
import org.slf4j.LoggerFactory

object Gen {
  private def random = ThreadLocalRandom.current

  val log = LoggerFacade(LoggerFactory.getLogger("Gen"))

  def script(complexity: Boolean = true, estimator: ScriptEstimator): Script = {
    val s = if (complexity) s"""
                               |${(for (b <- 1 to 10) yield {
                                 s"let a$b = blake2b256(base58'') != base58'' && keccak256(base58'') != base58'' && sha256(base58'') != base58'' && sigVerify(base58'333', base58'123', base58'567')"
                               }).mkString("\n")}
                               |
                               |${(for (b <- 1 to 10) yield { s"a$b" }).mkString("&&")} || true
       """.stripMargin
    else
      s"""
        |${recString(10)} || true
      """.stripMargin

    val script = ScriptCompiler(s, isAssetScript = false, estimator).explicitGet()

    script._1
  }

  def recString(n: Int): String =
    if (n <= 1) "true"
    else
      s"if (${recString(n - 1)}) then true else false"

  def oracleScript(oracle: KeyPair, data: Set[DataEntry[_]], estimator: ScriptEstimator): Script = {
    val conditions =
      data.map {
        case IntegerDataEntry(key, value) => s"""(extract(getInteger(oracle, "$key")) == $value)"""
        case BooleanDataEntry(key, _)     => s"""extract(getBoolean(oracle, "$key"))"""
        case BinaryDataEntry(key, value)  => s"""(extract(getBinary(oracle, "$key")) == $value)"""
        case StringDataEntry(key, value)  => s"""(extract(getString(oracle, "$key")) == "$value")"""
        case EmptyDataEntry(_)            => ???
      } reduce [String] { case (l, r) => s"$l && $r " }

    val src =
      s"""
         |let oracle = Address(base58'${oracle.toAddress}')
         |
         |match tx {
         |  case _: SetScriptTransaction => true
         |  case _                       => $conditions
         |}
       """.stripMargin

    val script = ScriptCompiler(src, isAssetScript = false, estimator).explicitGet()

    script._1
  }

  def multiSigScript(owners: Seq[KeyPair], requiredProofsCount: Int, estimator: ScriptEstimator): Script = {
    val accountsWithIndexes = owners.zipWithIndex
    val keyLets =
      accountsWithIndexes map {
        case (acc, i) =>
          s"let accountPK$i = base58'${acc.publicKey}'"
      } mkString "\n"

    val signedLets =
      accountsWithIndexes map {
        case (_, i) =>
          s"let accountSigned$i = if(sigVerify(tx.bodyBytes, tx.proofs[$i], accountPK$i)) then 1 else 0"
      } mkString "\n"

    val proofSum = accountsWithIndexes
      .map {
        case (_, ind) =>
          s"accountSigned$ind"
      }
      .mkString("let proofSum = ", " + ", "")

    val finalStatement = s"proofSum >= $requiredProofsCount"

    val src =
      s"""
       |$keyLets
       |
       |$signedLets
       |
       |$proofSum
       |
       |$finalStatement
      """.stripMargin

    val (script, _) = ScriptCompiler(src, isAssetScript = false, estimator)
      .explicitGet()

    script
  }

  def txs(minFee: Long, maxFee: Long, senderAccounts: Seq[KeyPair], recipientGen: Iterator[Address]): Iterator[Transaction] = {
    val senderGen = Iterator.randomContinually(senderAccounts)
    val feeGen    = Iterator.continually(minFee + random.nextLong(maxFee - minFee))
    transfers(senderGen, recipientGen, feeGen)
  }

  def transfers(senderGen: Iterator[KeyPair], recipientGen: Iterator[Address], feeGen: Iterator[Long]): Iterator[Transaction] = {
    val now = System.currentTimeMillis()

    senderGen
      .zip(recipientGen)
      .zip(feeGen)
      .zipWithIndex
      .map {
        case (((src, dst), fee), i) =>
          TransferTransaction.selfSigned(2.toByte, src, dst, Waves, fee, Waves, fee, ByteStr.empty, now + i)
      }
      .collect { case Right(x) => x }
  }

  def massTransfers(senderGen: Iterator[KeyPair], recipientGen: Iterator[Address], amountGen: Iterator[Long]): Iterator[Transaction] = {
    val now              = System.currentTimeMillis()
    val transferCountGen = Iterator.continually(random.nextInt(MassTransferTransaction.MaxTransferCount + 1))
    senderGen
      .zip(transferCountGen)
      .zipWithIndex
      .map {
        case ((sender, count), i) =>
          val transfers = List.tabulate(count)(_ => ParsedTransfer(recipientGen.next(), amountGen.next()))
          val fee       = 100000 + count * 50000
          MassTransferTransaction.selfSigned(1.toByte, sender, Waves, transfers, fee, now + i, ByteStr.empty)
      }
      .collect { case Right(tx) => tx }
  }

  val address: Iterator[Address] = Iterator.continually {
    val pk = Array.fill[Byte](KeyLength)(random.nextInt(Byte.MaxValue).toByte)
    Address.fromPublicKey(PublicKey(pk))
  }

  def address(uniqNumber: Int): Iterator[Address] = Iterator.randomContinually(address.take(uniqNumber).toSeq)

  def address(limitUniqNumber: Option[Int]): Iterator[Address] = limitUniqNumber.map(address(_)).getOrElse(address)

}
