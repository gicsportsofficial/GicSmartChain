package com.gicsports.utils

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

object SystemInformationReporter extends ScorexLogging {
  def report(config: Config): Unit = {
    val resolved = config.resolve()
    val configForLogs = {
      val orig = Seq(
        "GIC",
        "metrics"
      ).foldLeft(ConfigFactory.empty()) { case (r, path) => r.withFallback(resolved.withOnlyPath(path)) }

      Seq(
        "GIC.custom.genesis",
        "GIC.wallet",
        "GIC.rest-api.api-key-hash",
        "metrics.influx-db"
      ).foldLeft(orig)(_.withoutPath(_))
    }

    val renderOptions = ConfigRenderOptions
      .defaults()
      .setOriginComments(false)
      .setComments(false)
      .setFormatted(false)

    val logInfo: Seq[(String, Any)] = Seq(
      "Available processors" -> Runtime.getRuntime.availableProcessors,
      "Max memory available" -> Runtime.getRuntime.maxMemory
    ) ++ Seq(
      "os.name",
      "os.version",
      "os.arch",
      "java.version",
      "java.vendor",
      "java.home",
      "java.class.path",
      "user.dir",
      "sun.net.inetaddr.ttl",
      "sun.net.inetaddr.negative.ttl",
      "networkaddress.cache.ttl",
      "networkaddress.cache.negative.ttl"
    ).map { x =>
      x -> System.getProperty(x)
    } ++ Seq(
      "Configuration" -> configForLogs.root.render(renderOptions)
    )

    log.debug(logInfo.map { case (n, v) => s"$n: $v" }.mkString("\n"))
  }
}
