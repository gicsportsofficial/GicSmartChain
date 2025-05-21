package com.gicsports.events

import com.google.common.primitives.Ints
import com.gicsports.api.common.CommonBlocksApi
import com.gicsports.api.grpc._
import com.gicsports.common.state.ByteStr
import com.gicsports.common.utils.Base58
import com.gicsports.database.{DBExt, DBResource}
import com.gicsports.events.protobuf.BlockchainUpdated.Append.Body
import com.gicsports.events.protobuf.{BlockchainUpdated => PBBlockchainUpdated}
import com.gicsports.protobuf._
import com.gicsports.protobuf.block.PBBlock
import com.gicsports.utils.ScorexLogging
import monix.reactive.Observable
import org.iq80.leveldb.DB

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

class Loader(db: DB, blocksApi: CommonBlocksApi, target: Option[(Int, ByteStr)], streamId: String) extends ScorexLogging {
  private def loadBatch(res: DBResource, fromHeight: Int): Try[Seq[PBBlockchainUpdated]] = Try {
    res.iterator.seek(Ints.toByteArray(fromHeight))
    val buffer = ArrayBuffer[PBBlockchainUpdated]()

    while (res.iterator.hasNext && buffer.size < 100 && target.forall { case (h, _) => fromHeight + buffer.size <= h }) {
      buffer.append(Loader.parseUpdate(res.iterator.next().getValue, blocksApi, fromHeight + buffer.size))
    }

    for ((h, id) <- target if h == fromHeight + buffer.size - 1; u <- buffer.lastOption) {
      require(
        u.id.toByteArray.sameElements(id.arr),
        s"Stored update ${Base58.encode(u.id.toByteArray)} at ${u.height} does not match target $id at $h"
      )
    }

    buffer.toSeq
  }

  private def streamFrom(fromHeight: Int): Observable[PBBlockchainUpdated] = db.resourceObservable.flatMap { res =>
    loadBatch(res, fromHeight) match {
      case Success(nextBatch) =>
        if (nextBatch.isEmpty) Observable.empty[PBBlockchainUpdated]
        else Observable.fromIterable(nextBatch) ++ streamFrom(fromHeight + nextBatch.size)
      case Failure(exception) => Observable.raiseError(exception)
    }
  }

  def loadUpdates(fromHeight: Int): Observable[PBBlockchainUpdated] = {
    log.trace(s"[$streamId] Loading stored updates from $fromHeight up to ${target.fold("the most recent one") { case (h, id) => s"$id at $h" }}")
    streamFrom(fromHeight)
  }
}

object Loader {
  def parseUpdate(bs: Array[Byte], blocksApi: CommonBlocksApi, height: Int): PBBlockchainUpdated =
    PBBlockchainUpdated
      .parseFrom(bs)
      .update(
        _.append.update(
          _.body.modify {
            case Body.Block(value) =>
              Body.Block(value.copy(block = blocksApi.blockAtHeight(height).map {
                case (meta, txs) => PBBlock(Some(meta.header.toPBHeader), meta.signature.toByteString, txs.map(_._2.toPB))
              }))
            case other => other
          }
        )
      )

  def loadUpdate(res: DBResource, blocksApi: CommonBlocksApi, height: Int): PBBlockchainUpdated =
    parseUpdate(res.get(Repo.keyForHeight(height)), blocksApi, height)

}
