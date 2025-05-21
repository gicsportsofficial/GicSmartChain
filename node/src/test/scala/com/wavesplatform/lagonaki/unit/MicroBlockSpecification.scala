package com.gicsports.lagonaki.unit

import com.gicsports.account.KeyPair
import com.gicsports.block.serialization.MicroBlockSerializer
import com.gicsports.block.{Block, MicroBlock}
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.EitherExt2
import com.gicsports.mining.Miner
import com.gicsports.test._
import com.gicsports.transaction.Asset.{IssuedAsset, Waves}
import com.gicsports.transaction._
import com.gicsports.transaction.transfer._
import org.scalamock.scalatest.MockFactory

import scala.util.Random

class MicroBlockSpecification extends FunSuite with MockFactory {

  private val prevResBlockSig  = ByteStr(Array.fill(Block.BlockIdLength)(Random.nextInt(100).toByte))
  private val totalResBlockSig = ByteStr(Array.fill(Block.BlockIdLength)(Random.nextInt(100).toByte))
  private val reference        = Array.fill(Block.BlockIdLength)(Random.nextInt(100).toByte)
  private val sender           = KeyPair(reference.dropRight(2))
  private val gen              = KeyPair(reference)

  test("MicroBlock with txs bytes/parse roundtrip") {

    val ts = System.currentTimeMillis() - 5000
    val tr: TransferTransaction =
      TransferTransaction.selfSigned(1.toByte, sender, gen.toAddress, Waves, 5, Waves, 2, ByteStr.empty, ts + 1).explicitGet()
    val assetId = IssuedAsset(ByteStr(Array.fill(AssetIdLength)(Random.nextInt(100).toByte)))
    val tr2: TransferTransaction =
      TransferTransaction.selfSigned(1.toByte, sender, gen.toAddress, assetId, 5, Waves, 2, ByteStr.empty, ts + 2).explicitGet()

    val transactions = Seq(tr, tr2)

    val microBlock  = MicroBlock.buildAndSign(3.toByte, sender, transactions, prevResBlockSig, totalResBlockSig).explicitGet()
    val parsedBlock = MicroBlock.parseBytes(MicroBlockSerializer.toBytes(microBlock)).get

    assert(microBlock.signaturesValid().isRight)
    assert(parsedBlock.signaturesValid().isRight)

    assert(microBlock.signature == parsedBlock.signature)
    assert(microBlock.sender == parsedBlock.sender)
    assert(microBlock.totalResBlockSig == parsedBlock.totalResBlockSig)
    assert(microBlock.reference == parsedBlock.reference)
    assert(microBlock.transactionData == parsedBlock.transactionData)
    assert(microBlock == parsedBlock)
  }

  test("MicroBlock cannot be created with zero transactions") {

    val transactions       = Seq.empty[TransferTransaction]
    val eitherBlockOrError = MicroBlock.buildAndSign(3.toByte, sender, transactions, prevResBlockSig, totalResBlockSig)

    eitherBlockOrError should produce("cannot create empty MicroBlock")
  }

  test("MicroBlock cannot contain more than Miner.MaxTransactionsPerMicroblock") {

    val transaction =
      TransferTransaction.selfSigned(1.toByte, sender, gen.toAddress, Waves, 5, Waves, 1000, ByteStr.empty, System.currentTimeMillis()).explicitGet()
    val transactions = Seq.fill(Miner.MaxTransactionsPerMicroblock + 1)(transaction)

    val eitherBlockOrError = MicroBlock.buildAndSign(3.toByte, sender, transactions, prevResBlockSig, totalResBlockSig)
    eitherBlockOrError should produce("too many txs in MicroBlock")
  }
}
