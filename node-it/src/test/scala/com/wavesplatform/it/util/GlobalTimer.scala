package com.gicsports.it.util

import io.netty.util.{HashedWheelTimer, Timer}

object GlobalTimer {

  val instance: Timer = new HashedWheelTimer()
  sys.addShutdownHook {
    instance.stop()
  }

}
