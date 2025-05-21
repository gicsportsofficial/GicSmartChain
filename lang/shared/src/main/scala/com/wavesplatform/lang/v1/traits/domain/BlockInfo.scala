package com.gicsports.lang.v1.traits.domain
import com.gicsports.common.state.ByteStr

case class BlockInfo(timestamp: Long,
                     height: Int,
                     baseTarget: Long,
                     generationSignature: ByteStr,
                     generator: ByteStr,
                     generatorPublicKey: ByteStr,
                     vrf: Option[ByteStr])
