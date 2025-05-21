package com.gicsports.state

import com.gicsports.account.PublicKey
import com.gicsports.lang.script.Script

case class AccountScriptInfo(
    publicKey: PublicKey,
    script: Script,
    verifierComplexity: Long,
    complexitiesByEstimator: Map[Int, Map[String, Long]] = Map.empty
)
