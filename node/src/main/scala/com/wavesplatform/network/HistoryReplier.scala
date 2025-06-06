package com.gicsports.network

import com.gicsports.block.Block
import com.gicsports.history.History
import com.gicsports.network.HistoryReplier._
import com.gicsports.settings.SynchronizationSettings
import com.gicsports.utils.ScorexLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Sharable
class HistoryReplier(score: => BigInt, history: History, settings: SynchronizationSettings)(implicit ec: ExecutionContext)
    extends ChannelInboundHandlerAdapter
    with ScorexLogging {

  private def respondWith(ctx: ChannelHandlerContext, value: Future[Message]): Unit =
    value.onComplete {
      case Failure(e) => log.debug(s"${id(ctx)} Error processing request", e)
      case Success(value) =>
        if (ctx.channel().isOpen) {
          ctx.writeAndFlush(value)
        } else {
          log.trace(s"${id(ctx)} Channel is closed")
        }
    }

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case GetSignatures(otherSigs) =>
      respondWith(ctx, Future(Signatures(history.blockIdsAfter(otherSigs, settings.maxRollback))))

    case GetBlock(sig) =>
      respondWith(
        ctx,
        Future(history.loadBlockBytes(sig))
          .map {
            case Some((blockVersion, bytes)) =>
              RawBytes(if (blockVersion < Block.ProtoBlockVersion) BlockSpec.messageCode else PBBlockSpec.messageCode, bytes)
            case _ => throw new NoSuchElementException(s"Error loading block $sig")
          }
      )

    case MicroBlockRequest(microBlockId) =>
      respondWith(
        ctx,
        Future(history.loadMicroBlock(microBlockId)).map {
          case Some(microBlock) => RawBytes.fromMicroBlock(MicroBlockResponse(microBlock, microBlockId))
          case _                => throw new NoSuchElementException(s"Error loading microblock $microBlockId")
        }
      )

    case _: Handshake =>
      respondWith(ctx, Future(LocalScoreChanged(score)))

    case _ => super.channelRead(ctx, msg)
  }

  def cacheSizes: CacheSizes = CacheSizes(0, 0)
}

object HistoryReplier {
  case class CacheSizes(blocks: Long, microBlocks: Long)
}
