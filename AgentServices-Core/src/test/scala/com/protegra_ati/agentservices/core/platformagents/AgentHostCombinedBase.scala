package com.protegra_ati.agentservices.core.platformagents

import behaviors.Storage
import org.specs2.mutable._
import com.protegra_ati.agentservices.core._

import scala.collection.JavaConversions._
import com.protegra.agentservicesstore.extensions.StringExtensions._
import com.protegra.agentservicesstore.extensions.URIExtensions._
import com.protegra.agentservicesstore.extensions.URIExtensions._
import com.protegra.agentservicesstore.extensions.ResourceExtensions._

import org.junit._
import Assert._
import com.protegra.agentservicesstore.usage.AgentKVDBScope._
import com.protegra.agentservicesstore.usage.AgentKVDBScope.acT._
import com.protegra_ati.agentservices.core.schema._
import java.net.{URI}
import com.protegra_ati.agentservices.core.messages.content._
import com.protegra_ati.agentservices.core.util.Results

//import com.protegra_ati.agentservices.core.messages.search._
import com.protegra_ati.agentservices.core.messages._
import com.protegra_ati.agentservices.core.schema._
import com.protegra_ati.agentservices.core.schema.Data
import com.protegra_ati.agentservices.core.schema.disclosure._
import com.protegra_ati.agentservices.core.schema.PersistedRequest
import invitation._
import scala.util.Random

import org.specs2.mutable._
import org.specs2.time.Duration
import org.junit.runner._
import org.specs2.runner._
import com.protegra_ati.agentservices.core.events._
import com.protegra_ati.agentservices.core.schema.util._
import com.protegra_ati.agentservices.core._
import java.util.{Locale, UUID}
//import search.SearchResponse
//import com.protegra.config.ConfigurationManager
import com.protegra_ati.agentservices.core.util.serializer.Serializer


object AgentHostCombinedBase extends SpecificationWithJUnit with InitTestSetup with
Timeouts
{

  // inits test configuration
//  ConfigurationManager.getConfigurationManager().initForTest()

  def setupPAs(store: AgentHostStorePlatformAgent, ui: AgentHostUIPlatformAgent, cnxnUIStore: AgentCnxnProxy) :Unit  ={
    setupUI(ui,cnxnUIStore)
    setupStore(store,cnxnUIStore)
  }


  def setupUI(ui: AgentHostUIPlatformAgent, cnxnUIStore: AgentCnxnProxy) :Unit  ={
    ui._cnxnUIStore = cnxnUIStore
    val id = UUID.randomUUID
//    ui.setPrivateNetworkMode("KVDB")
    ui.initFromConfig(CONFIG_UI, id)
  }

  def setupStore(store: AgentHostStorePlatformAgent, cnxnUIStore: AgentCnxnProxy) :Unit  ={

    store._cnxnUIStore = cnxnUIStore
    val id = UUID.randomUUID
    //store._cnxnUserSelfConnectionsList = List(cnxnJenSelf, cnxnMikeSelf)
    store.setNetworkMode("Local")
//    store.setPrivateNetworkMode("KVDB")
    store.initFromConfig(CONFIG_STORE, id)
  }
 

  def setupIncrementalDisclosure(pa: AgentHostStorePlatformAgent, cnxn: AgentCnxnProxy) =
  {
//    ProfileDisclosedDataFactory
    // new DisclosedData[ Profile ](classOf[ Profile ], "Empty", "")
    val authorizedContentEmpty = ProfileDisclosedDataFactory.getDisclosedData(TrustLevel.Empty)
    //new DisclosedData[ Profile ](classOf[ Profile ], "Basic", "id,localeCode,firstName,lastName")
    val authorizedContentBasic = ProfileDisclosedDataFactory.getDisclosedData(TrustLevel.Basic)
    // new DisclosedData[ Profile ](classOf[ Profile ], "Trusted", "id,localeCode,firstName,lastName,description,emailAddress,country,region,city")
    val authorizedContentTrusted = ProfileDisclosedDataFactory.getDisclosedData(TrustLevel.Trusted)
    //save Profile authorized content for basic and trusted
    pa.store(pa._dbQ, cnxn, authorizedContentEmpty.toStoreKey, Serializer.serialize[ Data ](authorizedContentEmpty))
    pa.store(pa._dbQ, cnxn, authorizedContentBasic.toStoreKey, Serializer.serialize[ Data ](authorizedContentBasic))
    pa.store(pa._dbQ, cnxn, authorizedContentTrusted.toStoreKey, Serializer.serialize[ Data ](authorizedContentTrusted))
    Thread.sleep(TIMEOUT_LONG)

    val appIdTrusted = AppIdDisclosedDataFactory.getDisclosedData(TrustLevel.Trusted) //DisclosedData[ AppId ](classOf[ AppId ], "Trusted", "id,localeCode,name");
    val appIdBasic = AppIdDisclosedDataFactory.getDisclosedData(TrustLevel.Basic) //DisclosedData[ AppId ](classOf[ AppId ], "Basic", "id,localeCode,name");
    val appIdCustom = AppIdDisclosedDataFactory.getDisclosedData(TrustLevel.Custom) //DisclosedData[ AppId ](classOf[ AppId ], "Custom", "id,localeCode,name");

    //save appId authorized content for basic basic trusted
    pa.store(pa._dbQ, cnxn, appIdTrusted.toStoreKey, Serializer.serialize[ Data ](appIdTrusted))
    pa.store(pa._dbQ, cnxn, appIdBasic.toStoreKey, Serializer.serialize[ Data ](appIdBasic))
    pa.store(pa._dbQ, cnxn, appIdCustom.toStoreKey, Serializer.serialize[ Data ](appIdCustom))
    Thread.sleep(TIMEOUT_LONG)
  }

  def getConnection(selfId: String, targetId: String): Connection =
  {
    val newConn = ConnectionFactory.createConnection("New Connection", ConnectionCategory.None.toString, ConnectionCategory.None.toString, "Custom", selfId, targetId);
    newConn
  }

  def setupConnection(store: AgentHostStorePlatformAgent, selfId: UUID, targetId: UUID): Connection =
  {
    setupConnection(store, selfId.toString, targetId.toString)
  }

  def setupConnection(store: AgentHostStorePlatformAgent, selfId: String, targetId: String): Connection =
  {
    val newConn = ConnectionFactory.createConnection("New Connection", ConnectionCategory.None.toString, ConnectionCategory.None.toString, "Custom", selfId, targetId);
    val sourceSelfCnxn = new AgentCnxnProxy(newConn.writeCnxn.src, "", newConn.writeCnxn.src)

    store.processNewConnection(newConn, sourceSelfCnxn)
    Thread.sleep(TIMEOUT_SHORT)
    newConn
  }

  def setupConnection(store: AgentHostStorePlatformAgent, newConn: Connection): Connection =
  {
    val sourceSelfCnxn = new AgentCnxnProxy(newConn.writeCnxn.src, "", newConn.writeCnxn.src)
    store.processNewConnection(newConn, sourceSelfCnxn)
    Thread.sleep(TIMEOUT_SHORT)
    newConn
  }

  def setupPersistedConnection(pa: AgentHostStorePlatformAgent, selfId: UUID, targetId: UUID): Connection =
  {
    setupPersistedConnection(pa, selfId.toString, targetId.toString)
  }

  def setupPersistedConnection(pa: AgentHostStorePlatformAgent, selfId: String, targetId: String): Connection =
  {
    val newConn = ConnectionFactory.createConnection("New Connection", ConnectionCategory.None.toString, ConnectionCategory.None.toString, "Trusted", selfId, targetId);
    val sourceSelfCnxn = new AgentCnxnProxy(newConn.writeCnxn.src, "", newConn.writeCnxn.src)

    pa.processNewConnection(newConn, sourceSelfCnxn)
    if ( selfId != targetId )
      pa.updateDataById(sourceSelfCnxn, newConn)

    Thread.sleep(TIMEOUT_SHORT)
    newConn
  }

  /**
   * Persists given connection
   * @param pa store
   * @param selfTargetConn conn. to be persisted
   * @return reference to  selfTargetConn passed truth
   */
  def setupPersistedConnection(pa: AgentHostStorePlatformAgent, selfTargetConn: Connection): Connection =
  {
    val sourceSelfCnxn = new AgentCnxnProxy(selfTargetConn.writeCnxn.src, "", selfTargetConn.writeCnxn.src)

    pa.processNewConnection(selfTargetConn, sourceSelfCnxn)
    if ( !selfTargetConn.readCnxn.src.toString.equals(selfTargetConn.readCnxn.trgt.toString) )
      pa.updateDataById(sourceSelfCnxn, selfTargetConn)

    Thread.sleep(TIMEOUT_SHORT)
    selfTargetConn
  }


  def setupPersistedConnection(pa: AgentHostStorePlatformAgent, alias: String, connectionType: String, selfId: String, targetId: String): Connection =
  {
    val newConn = ConnectionFactory.createConnection(alias, ConnectionCategory.None.toString, ConnectionCategory.None.toString, connectionType, selfId, targetId);
    val sourceSelfCnxn = new AgentCnxnProxy(newConn.writeCnxn.src, "", newConn.writeCnxn.src)

    pa.processNewConnection(newConn, sourceSelfCnxn)
    if ( selfId != targetId )
      pa.updateDataById(sourceSelfCnxn, newConn)

    Thread.sleep(TIMEOUT_SHORT)
    newConn
  }

  def setupSelfConnection(pa: AgentHostStorePlatformAgent, cnxn: AgentCnxnProxy, alias: String, id: String, connectionType: String): Connection =
  {
    val self = ConnectionFactory.createConnection(alias, ConnectionCategory.Self.toString, ConnectionCategory.Self.toString, connectionType, id, id)
    pa.store(pa._dbQ, cnxn, self.toStoreKey, Serializer.serialize[ Data ](self))
    pa.generateSystemData(self.writeCnxn, self)
    //
    self
  }

  def setAppId(ui: AgentHostUIPlatformAgent, conn: Connection, businessName: String) =
  {
    val msg = new SetContentRequest(new EventKey(UUID.randomUUID, ""), new AppId(businessName), null)
    msg.targetCnxn = conn.writeCnxn
    msg.originCnxn = ui._cnxnUIStore
    ui.send(msg)
    Thread.sleep(500)
  }

  def setProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, localeCode: String): Unit =
  {
    val Profile = new Profile("testFirst", "testLast", "testDesc", "bc123@test.com", "CA", "someCAprovince", "city", "postalCode", "website")
    setProfile(ui, cnxn, agentSessionId, tag, localeCode, Profile)
  }

  def setProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String): Unit =
  {
    val Profile = new Profile("testFirst", "testLast", "testDesc", "bc123@test.com", "CA", "someCAprovince", "city", "postalCode", "website")
    setProfile(ui, cnxn, agentSessionId, tag, Locale.ENGLISH.toString(), Profile)
  }

  def setProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, localeCode: String, Profile: Profile): Unit =
  {
    Profile.localeCode = localeCode
    val setReq = new SetContentRequest(new EventKey(agentSessionId, tag), Profile, null)
    setReq.originCnxn = cnxn
    setReq.targetCnxn = cnxn
    ui.send(setReq)
  }

  def setAppId(ui: AgentHostUIPlatformAgent, self: Connection): AppId =
  {
    val appId = new AppId("TestApp")
    appId.setDefaultValues(false)
    setContent(ui, self, appId)
    appId
  }

  def setContent(ui: AgentHostUIPlatformAgent, conn: Connection, data: Data) =
  {
    val msg = new SetContentRequest(new EventKey(UUID.randomUUID, ""), data, null)

    msg.targetCnxn = conn.writeCnxn
    msg.originCnxn = conn.readCnxn
    ui.send(msg)
    Thread.sleep(500)

  }


  def countProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String): Int =
  {
    countProfile(ui, cnxn, agentSessionId, tag, None)

  }

  def countProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, localeCode: Option[ String ]): Int =
  {
    val query: Profile = new Profile()
    localeCode match {
      case Some(x) => query.localeCode = x
      case _ => {}
    }
    count(ui, cnxn, agentSessionId, tag, query)
  }

  def countPersistedInvitationRequests(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, localeCode: Option[ String ]): Int =
  {
    val query: PersistedMessage[ InvitationRequest ] = new PersistedMessage[ InvitationRequest ]()
    localeCode match {
      case Some(x) => query.localeCode = x
      case _ => {}
    }
    count(ui, cnxn, agentSessionId, tag, query)
  }

  def count(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: Data): Int =
  {
    //tag needs to be random otherwise only the 1st listen will wake up by the time the 4th listen is applying the must be_==
    //we intend to do many separate listens
    val tagUnique = tag + UUID.randomUUID().toString
    val countKey = Results.getKey()
    ui.addListener(agentSessionId, "", new MessageEventAdapter(tagUnique)
    {
      override def getContentResponseReceived(e: GetContentResponseReceivedEvent) =
      {
        println("===========================getContentResponseReceived: " + e)
        e.msg match {
          case x: GetContentResponse => {
            Results.count(countKey, x.data.size)

            println("size received : " +  x.data.size)
          }
          case _ => {}
        }
      }
    });

    val getReq = new GetContentRequest(new EventKey(agentSessionId, tagUnique), query)
    getReq.originCnxn = cnxn
    getReq.targetCnxn = cnxn
    ui.send(getReq)
    Results.counted(countKey)
  }

  def countCompositeProfile(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String): Int =
  {
    val query = new CompositeData[ Profile ](Connection.SEARCH_ALL, new Profile());
    countComposite(ui, cnxn, agentSessionId, tag, query)
  }

  def countAudit(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String): Int =
  {
    val query = new CompositeData[ AuditLogItem ](Connection.SEARCH_ALL, new AuditLogItem());
    countComposite(ui, cnxn, agentSessionId, tag, query)
  }

  def countComposite(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: CompositeData[ _ <: Data ]): Int =
  {
    val countKey = Results.getKey()
    val tagUnique = tag + UUID.randomUUID().toString
    ui.addListener(agentSessionId, UUID.randomUUID().toString, new MessageEventAdapter(tagUnique)
    {
      override def getContentResponseReceived(e: GetContentResponseReceivedEvent) =
      {
        e.msg match {
          case x: GetContentResponse => {
            Results.count(countKey, x.data.size)
            println("size received : " +  x.data.size)
          }
          case _ => {}
        }
      }
    });

    val getReq = new GetContentRequest(new EventKey(agentSessionId, tagUnique), query)

    getReq.originCnxn = cnxn
    getReq.targetCnxn = cnxn
    ui.send(getReq)
    Results.counted(countKey)
  }

  def performOperationOnFetchedData(handler: (Data, AgentCnxnProxy) => Unit,
    q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String) =
  {
    var actual: Data = null
    handler(fetchData(q, cnxn, key), cnxn)

    def fetchData(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Data =
    {
      q.fetch[ Data ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: Data))
      trySleep(actual)
      println("----fetched data:" + actual)
      return actual
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: Data) =
    {
      println("-----getMustBe - get received : " + data)
      actual = data;
    }

  }


  def performAssertOperationOnFetchedData(handler: (Data, AgentCnxnProxy) => Boolean, q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String) =
  {
    var actual: Data = null
    handler(fetchData(q, cnxn, key), cnxn) must be_==(true).eventually(10, TIMEOUT_EVENTUALLY)

    def fetchData(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Data =
    {
      q.fetch[ Data ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: Data))
      trySleep(actual)
      println("----fetched data:" + actual)
      return actual
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: Data) =
    {
      println("-----getMustBe - get received : " + data)
      actual = data;
    }

  }

  def performOperationOnFetchedPost(handler: (Post, AgentCnxnProxy, AgentCnxnProxy) => Boolean, q: BasePlatformAgent with Storage, selfCnxn: AgentCnxnProxy, directCnxn: AgentCnxnProxy, key: String) =
  {
    var actual: Post = null

    println("attempting assert")

    handler(fetchPost(q, directCnxn, key), selfCnxn, directCnxn) must be_==(true).eventually(10, TIMEOUT_EVENTUALLY)

    def fetchPost(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Post =
    {
      println("BasePlatformAgent is = " + q)
      q.fetch[ Post ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: Post))
      trySleep(actual)
      println("----fetched data:" + actual)
      return actual
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: Post) =
    {
      println("-----getMustBe - get received : " + data)
      actual = data;
    }

  }


  def fetchMustBe(handler: ( Post ) => Boolean)(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String) =
  {
    var actual: Post = null

    println("attempting assert")

    ( handler(fetchPost(q, cnxn, key)) ) must be_==(true).eventually(10, TIMEOUT_EVENTUALLY)

    def fetchPost(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Post =
    {
      println("BasePlatformAgent is = " + q)
      q.fetch[ Post ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: Post))
      trySleep(actual)
      println("----fetched data:" + actual)
      return actual
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: Post) =
    {
      println("-----getMustBe - get received : " + data)
      actual = data;
    }

  }


  def fetchMustBe(expected: Data)(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String) =
  {
    var actual: Data = null

    println("attempting assert")

    fetchData(q, cnxn, key) must be_==(expected).eventually(5, TIMEOUT_EVENTUALLY)
    println("fetchMustBe expecting: " + expected)
    def fetchData(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Data =
    {
      println("BasePlatformAgent is = " + q)
      q.fetch[ Data ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: Data))
      trySleep(actual)
      println("----fetched data:" + actual)
      return actual
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: Data) =
    {
      println("-----getMustBe - get received : " + data)
      actual = data;
    }

  }

  def countMustBe(expected: Int)(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String) =
  {
    var found = 0

    println("attempting count")
    fetchCount(q, cnxn, key) must be_==(expected).eventually(5, TIMEOUT_EVENTUALLY)

    def fetchCount(q: BasePlatformAgent with Storage, cnxn: AgentCnxnProxy, key: String): Int =
    {
      q.fetchList[ Any ](q._dbQ, cnxn, key, handleFetch(_: AgentCnxnProxy, _: List[ Any ]))
      trySleep(found)
      return found
    }

    def handleFetch(cnxn: AgentCnxnProxy, data: List[ Any ]) =
    {
      println("getMustBe - get received : " + data)
      for ( e <- data ) {
        println("*************fetchCount - received : " + e.toString)
        if ( e.toString != "" )
          found += 1
      }
    }
  }

  def countConnectionsByCategory(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: Data, category: String): Int =
  {
    countConnectionsByComparison(ui, cnxn, agentSessionId, tag, query, compareConnectionCategory(_: Connection, category))
  }

  def compareConnectionCategory(actual: Connection, expectedCategory: String): Boolean =
  {
    actual.category.equals(expectedCategory)
  }


  def countConnectionsByType(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: Data, connectionType: String): Int =
  {
    countConnectionsByComparison(ui, cnxn, agentSessionId, tag, query, compareConnectionType(_: Connection, connectionType))
  }

  def compareConnectionType(actual: Connection, expectedConnectionType: String): Boolean =
  {
    actual.connectionType.equals(expectedConnectionType)
  }


  def countConnectionsByName(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: Data, connectionName: String): Int =
  {
    countConnectionsByComparison(ui, cnxn, agentSessionId, tag, query, compareConnectionName(_: Connection, connectionName))
  }

  def compareConnectionName(actual: Connection, expectedConnectionName: String): Boolean =
  {
    actual.alias.equals(expectedConnectionName)
  }


  def countConnectionsByComparison(ui: AgentHostUIPlatformAgent, cnxn: AgentCnxnProxy, agentSessionId: UUID, tag: String, query: Data, compare: ( Connection ) => Boolean): Int =
  {
    //tag needs to be random otherwise only the 1st listen will wake up by the time the 4th listen is applying the must be_==
    //we intend to do many separate listens
    val tagUnique = tag + UUID.randomUUID().toString
    val countKey = Results.getKey()
    ui.addListener(agentSessionId, "", new MessageEventAdapter(tagUnique)
    {
      override def getContentResponseReceived(e: GetContentResponseReceivedEvent) =
      {
        println("===========================getContentResponseReceived: " + e)
        e.msg match {
          case x: GetContentResponse => {
              val count = x.data.size
              Results.count(countKey, count)
              x.data.foreach(response => {
                  response match {
                    case x: Connection => {
                      println("connection found=" + x)
                      if ( !compare(x) ) {
                        Results.voidCount(countKey)
                      }
                    }
                    case _ => {
                      Results.voidCount(countKey)
                    }
                  }
              }
              )
          }
          case _ => {}
        }
      }
    })

    val getReq = new GetContentRequest(new EventKey(agentSessionId, tagUnique), query)
    getReq.originCnxn = cnxn
    getReq.targetCnxn = cnxn
    ui.send(getReq)

    Results.counted(countKey)
  }

}
