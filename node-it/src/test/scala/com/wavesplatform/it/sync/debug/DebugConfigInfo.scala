package com.gicsports.it.sync.debug

import com.typesafe.config.Config
import com.gicsports.it.api.SyncHttpApi._
import com.gicsports.it.{BaseFunSuite, NodeConfigs}

class DebugConfigInfo extends BaseFunSuite {

  override protected val nodeConfigs: Seq[Config] = NodeConfigs.newBuilder.withDefault(1).build()

  test("getting a configInfo") {
    nodes.head.getWithApiKey(s"/debug/configInfo?full=false")
    nodes.last.getWithApiKey(s"/debug/configInfo?full=true")
  }

}
