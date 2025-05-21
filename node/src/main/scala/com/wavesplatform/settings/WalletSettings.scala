package com.gicsports.settings

import java.io.File

import com.gicsports.common.state.ByteStr

case class WalletSettings(file: Option[File], password: Option[String], seed: Option[ByteStr])
