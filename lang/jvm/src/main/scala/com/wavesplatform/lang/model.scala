package com.gicsports.lang

import com.gicsports.lang.contract.DApp

import scala.beans.BeanProperty

case class ArgNameWithType(@BeanProperty name: String, @BeanProperty `type`: String)

case class DAppWithMeta(@BeanProperty dApp: DApp, @BeanProperty meta: Meta)

case class Meta(@BeanProperty functionSignatures: java.util.Map[String, java.util.List[ArgNameWithType]])
