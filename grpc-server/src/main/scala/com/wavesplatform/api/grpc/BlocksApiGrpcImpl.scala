package com.gicsports.api.grpc

import com.google.protobuf.empty.Empty
import com.gicsports.api.BlockMeta
import com.gicsports.api.common.CommonBlocksApi
import com.gicsports.api.grpc.BlockRangeRequest.Filter
import com.gicsports.api.grpc.BlockRequest.Request
import com.gicsports.api.http.ApiError.BlockDoesNotExist
import com.gicsports.protobuf._
import com.gicsports.protobuf.block.PBBlock
import com.gicsports.state.TxMeta
import com.gicsports.transaction.Transaction
import io.grpc.stub.StreamObserver
import monix.execution.Scheduler

import scala.concurrent.Future

class BlocksApiGrpcImpl(commonApi: CommonBlocksApi)(implicit sc: Scheduler) extends BlocksApiGrpc.BlocksApi {
  import BlocksApiGrpcImpl._

  override def getCurrentHeight(request: Empty): Future[Int] = {
    Future.successful(commonApi.currentHeight)
  }

  override def getBlockRange(request: BlockRangeRequest, responseObserver: StreamObserver[BlockWithHeight]): Unit = responseObserver.interceptErrors {
    val stream =
      if (request.includeTransactions)
        commonApi
          .blocksRange(request.fromHeight, request.toHeight)
          .map(toBlockWithHeight)
      else
        commonApi
          .metaRange(request.fromHeight, request.toHeight)
          .map { meta =>
            BlockWithHeight(Some(PBBlock(Some(meta.header.toPBHeader), meta.signature.toByteString)), meta.height)
          }

    responseObserver.completeWith(request.filter match {
      case Filter.GeneratorPublicKey(publicKey) => stream.filter(_.getBlock.getHeader.generator.toPublicKey == publicKey.toPublicKey)
      case Filter.GeneratorAddress(address)     => stream.filter(_.getBlock.getHeader.generator.toAddress == address.toAddress)
      case Filter.Empty                         => stream
    })
  }

  override def getBlock(request: BlockRequest): Future[BlockWithHeight] = Future {
    (request.request match {
      case Request.BlockId(blockId) =>
        if (request.includeTransactions)
          commonApi
            .block(blockId.toByteStr)
            .map(toBlockWithHeight)
        else commonApi.meta(blockId.toByteStr).map(toBlockWithHeight)

      case Request.Height(height) =>
        val actualHeight = if (height > 0) height else commonApi.currentHeight + height
        if (request.includeTransactions)
          commonApi
            .blockAtHeight(actualHeight)
            .map(toBlockWithHeight)
        else commonApi.metaAtHeight(actualHeight).map(toBlockWithHeight)

      case Request.Empty =>
        None
    }).explicitGetErr(BlockDoesNotExist)
  }
}

object BlocksApiGrpcImpl {
  private def toBlockWithHeight(v: (BlockMeta, Seq[(TxMeta, Transaction)])) =
    BlockWithHeight(Some(PBBlock(Some(v._1.header.toPBHeader), v._1.signature.toByteString, v._2.map(_._2.toPB))), v._1.height)

  private def toBlockWithHeight(m: BlockMeta) =
    BlockWithHeight(Some(PBBlock(Some(m.header.toPBHeader), m.signature.toByteString)), m.height)
}
