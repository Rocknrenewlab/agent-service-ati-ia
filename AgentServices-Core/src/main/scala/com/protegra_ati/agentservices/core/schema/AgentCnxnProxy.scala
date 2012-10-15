package com.protegra_ati.agentservices.core.schema

import com.protegra.agentservicesstore.usage.AgentKVDBScope.acT._
import com.protegra_ati.agentservices.core.schema._

import java.net.URI
import com.protegra_ati.agentservices.core.util.serializer.UseKryoSerialization

case class AgentCnxnProxy(
  val src: URI,
  val label: String,
  val trgt: URI
  ) extends UseKryoSerialization
{

  def this() = this(null, null, null)

  def toAgentCnxn() : com.protegra.agentservicesstore.usage.AgentKVDBScope.acT.AgentCnxn = {
    new AgentCnxn(src, label, trgt)
  }

}