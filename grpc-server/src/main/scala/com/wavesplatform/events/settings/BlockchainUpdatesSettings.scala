package com.gicsports.events.settings

import scala.concurrent.duration.FiniteDuration
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import net.ceedubs.ficus.readers.{Generated, ValueReader}
import net.ceedubs.ficus.readers.namemappers.implicits.hyphenCase

case class BlockchainUpdatesSettings(grpcPort: Int, minKeepAlive: FiniteDuration)

object BlockchainUpdatesSettings {
  implicit val valueReader: Generated[ValueReader[BlockchainUpdatesSettings]] = arbitraryTypeValueReader
}
