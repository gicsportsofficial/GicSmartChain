package com.gicsports

import com.gicsports.utils.{SystemTime, Time}
import org.scalatest.Suite

trait NTPTime { _: Suite =>
  protected val ntpTime: Time = SystemTime

  protected def ntpNow: Long = ntpTime.getTimestamp()
}
