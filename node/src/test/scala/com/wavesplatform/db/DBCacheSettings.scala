package com.gicsports.db
import com.typesafe.config.ConfigFactory
import com.gicsports.settings.WavesSettings

trait DBCacheSettings {
  lazy val dbSettings        = WavesSettings.fromRootConfig(ConfigFactory.load()).dbSettings
  lazy val maxCacheSize: Int = dbSettings.maxCacheSize
}
