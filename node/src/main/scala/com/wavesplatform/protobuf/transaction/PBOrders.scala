package com.gicsports.protobuf.transaction

import com.gicsports.transaction as vt
import com.gicsports.account.{AddressScheme, PublicKey}
import com.gicsports.lang.ValidationError
import com.gicsports.protobuf.*
import com.gicsports.protobuf.order.AssetPair
import com.gicsports.protobuf.order.Order.{PriceMode, Sender}
import com.gicsports.protobuf.order.Order.PriceMode.{ASSET_DECIMALS, FIXED_DECIMALS, DEFAULT as DEFAULT_PRICE_MODE}
import com.gicsports.transaction.assets.exchange.OrderPriceMode.{AssetDecimals, FixedDecimals, Default as DefaultPriceMode}
import vt.assets.exchange.OrderAuthentication
import com.gicsports.transaction.TxValidationError.GenericError
import com.gicsports.transaction.assets.exchange.OrderType
import com.gicsports.transaction.{TxExchangeAmount, TxMatcherFee, TxOrderPrice}
import com.gicsports.{transaction => vt}

object PBOrders {
  import com.gicsports.protobuf.utils.PBImplicitConversions.*

  def vanilla(order: PBOrder): Either[ValidationError, VanillaOrder] =
    for {
      amount     <- TxExchangeAmount(order.amount)(GenericError(TxExchangeAmount.errMsg))
      price      <- TxOrderPrice(order.price)(GenericError(TxOrderPrice.errMsg))
      orderType  <- vanillaOrderType(order.orderSide)
      matcherFee <- TxMatcherFee(order.getMatcherFee.longAmount)(GenericError(TxMatcherFee.errMsg))
    } yield {
      VanillaOrder(
        order.version.toByte,
      order.sender match {
        case Sender.SenderPublicKey(value) => OrderAuthentication.OrderProofs(PublicKey(value.toByteStr), order.proofs.map(_.toByteStr))
        case Sender.Eip712Signature(sig)   => OrderAuthentication.Eip712Signature(sig.toByteStr)
        case Sender.Empty                  => throw new IllegalArgumentException("Order should have either senderPublicKey or eip712Signature")
      },
        PublicKey(order.matcherPublicKey.toByteArray),
        vt.assets.exchange
          .AssetPair(PBAmounts.toVanillaAssetId(order.getAssetPair.amountAssetId), PBAmounts.toVanillaAssetId(order.getAssetPair.priceAssetId)),
        orderType,
        amount,
        price,
        order.timestamp,
        order.expiration,
        matcherFee,
        PBAmounts.toVanillaAssetId(order.getMatcherFee.assetId),
        order.priceMode match {
        case DEFAULT_PRICE_MODE        => DefaultPriceMode
        case ASSET_DECIMALS            => AssetDecimals
        case FIXED_DECIMALS            => FixedDecimals
        case PriceMode.Unrecognized(v) => throw new IllegalArgumentException(s"Unknown order price mode: $v")
      }
      )
    }

  def protobuf(order: VanillaOrder): PBOrder = {
    PBOrder(
      AddressScheme.current.chainId,
      order.matcherPublicKey.toByteString,
      Some(AssetPair(PBAmounts.toPBAssetId(order.assetPair.amountAsset), PBAmounts.toPBAssetId(order.assetPair.priceAsset))),
      order.orderType match {
        case vt.assets.exchange.OrderType.BUY  => PBOrder.Side.BUY
        case vt.assets.exchange.OrderType.SELL => PBOrder.Side.SELL
      },
      order.amount.value,
      order.price.value,
      order.timestamp,
      order.expiration,
      Some((order.matcherFeeAssetId, order.matcherFee.value)),
      order.version,
      order.proofs.map(_.toByteString),
      order.priceMode match {
        case DefaultPriceMode => DEFAULT_PRICE_MODE
        case AssetDecimals    => ASSET_DECIMALS
        case FixedDecimals    => FIXED_DECIMALS
      },
      order.orderAuthentication match {
        case OrderAuthentication.OrderProofs(key, _)        => Sender.SenderPublicKey(key.toByteString)
        case OrderAuthentication.Eip712Signature(signature) => Sender.Eip712Signature(signature.toByteString)
      }
    )
  }

  private def vanillaOrderType(orderSide: com.gicsports.protobuf.order.Order.Side): Either[GenericError, OrderType] =
    orderSide match {
      case PBOrder.Side.BUY             => Right(vt.assets.exchange.OrderType.BUY)
      case PBOrder.Side.SELL            => Right(vt.assets.exchange.OrderType.SELL)
      case PBOrder.Side.Unrecognized(v) => Left(GenericError(s"Unknown order type: $v"))
    }
}
