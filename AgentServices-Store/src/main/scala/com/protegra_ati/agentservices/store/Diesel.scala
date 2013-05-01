// -*- mode: Scala;-*- 
// Filename:    Diesel.scala 
// Authors:     lgm                                                    
// Creation:    Sat Apr 27 00:25:52 2013 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.biosimilarity.evaluator.distribution

import com.biosimilarity.evaluator.dsl._

import com.protegra_ati.agentservices.store._

import com.protegra_ati.agentservices.store.extensions.URIExtensions._
//import com.protegra_ati.agentservices.store.extensions.URMExtensions._
import com.protegra_ati.agentservices.store.extensions.MonikerExtensions._

import com.biosimilarity.lift.model.ApplicationDefaults
import com.biosimilarity.lift.model.store.xml._
import com.biosimilarity.lift.model.store._
import com.biosimilarity.lift.model.agent._
import com.biosimilarity.lift.model.msg._
import com.biosimilarity.lift.lib._
import com.biosimilarity.lift.lib.moniker._
import net.liftweb.amqp._

import scala.util.continuations._ 
import scala.concurrent.{Channel => Chan, _}
//import scala.concurrent.cpsops._
import scala.xml._
import scala.collection.mutable.Map
import scala.collection.mutable.MapProxy
import scala.collection.mutable.HashMap
import scala.collection.mutable.LinkedHashMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Stack
import scala.collection.mutable.MutableList

import com.rabbitmq.client._

import org.prolog4j._

import com.mongodb.casbah.Imports._

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver

import biz.source_code.base64Coder.Base64Coder

import javax.xml.transform.OutputKeys

import java.util.UUID
import java.net.URI
import java.util.Properties
import java.io.File
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream

package diesel {
  import scala.xml._
  import scala.xml.XML._
  import scala.collection.mutable.Buffer
  import scala.collection.mutable.ListBuffer

  object DieselEngineScope
	 extends AgentKVDBMongoNodeScope[String,String,String,ConcreteHL.HLExpr]
	 with UUIDOps
	 with Serializable
  {
    import SpecialKURIDefaults._
    import identityConversions._

    type ACTypes = AgentCnxnTypes
    object TheACT extends ACTypes
    override def protoAgentCnxnTypes : ACTypes = TheACT

    type MTTypes = MonadicTermTypes[String,String,String,ConcreteHL.HLExpr]
    object TheMTT extends MTTypes with Serializable
    override def protoTermTypes : MTTypes = TheMTT

    type DATypes = DistributedAskTypes
    object TheDAT extends DATypes with Serializable
    override def protoAskTypes : DATypes = TheDAT
    
    override type MsgTypes = DTSMSHRsrc   
    override type RsrcMsgTypes = DTSMSHRsrc   
    
    @transient
    val protoDreqUUID = getUUID()
    @transient
    val protoDrspUUID = getUUID()    

    @transient
    lazy val aLabel = new CnxnCtxtLeaf[String,String,String]( Left( "a" ) )

    object MonadicDRsrcMsgs extends RsrcMsgTypes with Serializable {
      
      @transient
      override def protoDreq : DReq = MDGetRequest( aLabel )
      @transient
      override def protoDrsp : DRsp = MDGetResponse( aLabel, ConcreteHL.Bottom )
      @transient
      override def protoJtsreq : JTSReq =
	JustifiedRequest(
	  protoDreqUUID,
	  new URI( "agent", protoDreqUUID.toString, "/invitation", "" ),
	  new URI( "agent", protoDreqUUID.toString, "/invitation", "" ),
	  getUUID(),
	  protoDreq,
	  None
	)
      @transient
      override def protoJtsrsp : JTSRsp = 
	JustifiedResponse(
	  protoDreqUUID,
	  new URI( "agent", protoDrspUUID.toString, "/invitation", "" ),
	  new URI( "agent", protoDrspUUID.toString, "/invitation", "" ),
	  getUUID(),
	  protoDrsp,
	  None
	)
      override def protoJtsreqorrsp : JTSReqOrRsp =
	Left( protoJtsreq )
    }
    
    override def protoMsgs : MsgTypes = MonadicDRsrcMsgs
    override def protoRsrcMsgs : RsrcMsgTypes = MonadicDRsrcMsgs

    object Being extends AgentPersistenceScope with Serializable {      
      override type EMTypes = ExcludedMiddleTypes[mTT.GetRequest,mTT.GetRequest,mTT.Resource]
      object theEMTypes extends ExcludedMiddleTypes[mTT.GetRequest,mTT.GetRequest,mTT.Resource]
       with Serializable
      {
	case class PrologSubstitution( soln : LinkedHashMap[String,CnxnCtxtLabel[String,String,String]] )
	   extends Function1[mTT.Resource,Option[mTT.Resource]] {
	     override def apply( rsrc : mTT.Resource ) = {
	       Some( mTT.RBoundHM( Some( rsrc ), Some( soln ) ) )
	     }
	   }
	override type Substitution = PrologSubstitution	
      }      

      override def protoEMTypes : EMTypes =
	theEMTypes

      object AgentKVDBNodeFactory
	     extends BaseAgentKVDBNodeFactoryT
	     with AgentKVDBNodeFactoryT
	     with WireTap with Journalist
	     with Serializable {	  	       
	type AgentCache[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse] = AgentKVDB[ReqBody,RspBody]
        //type AgentNode[Rq <: PersistedKVDBNodeRequest, Rs <: PersistedKVDBNodeResponse] = AgentKVDBNode[Rq,Rs]

	override def tap [A] ( fact : A ) : Unit = {
	  reportage( fact )
	}

	override def mkCache[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse]( 
	  here : URI,
	  configFileName : Option[String]
	) : AgentCache[ReqBody,RspBody] = {
	  new AgentKVDB[ReqBody, RspBody](
	    MURI( here ),
	    configFileName
	  ) with Blobify with AMQPMonikerOps {		
	    class StringMongoDBManifest(
	      override val storeUnitStr : String,
	      @transient override val labelToNS : Option[String => String],
	      @transient override val textToVar : Option[String => String],
	      @transient override val textToTag : Option[String => String]
	    )
	    extends MongoDBManifest( /* database */ ) {
	      override def valueStorageType : String = {
		throw new Exception( "valueStorageType not overriden in instantiation" )
	      }
	      override def continuationStorageType : String = {
		throw new Exception( "continuationStorageType not overriden in instantiation" )
	      }
	      
	      override def storeUnitStr[Src,Label,Trgt]( cnxn : Cnxn[Src,Label,Trgt] ) : String = {     
		cnxn match {
		  case CCnxn( s, l, t ) => s.toString + l.toString + t.toString
		  case acT.AgentCnxn( s, l, t ) => s.getHost + l.toString + t.getHost
		}	    
	      }	
	      
	      def kvNameSpace : String = "record"
	      def kvKNameSpace : String = "kRecord"
	      
	      def compareNameSpace( ns1 : String, ns2 : String ) : Boolean = {
		ns1.equals( ns2 )
	      }
	      
	      override def asStoreValue(
		rsrc : mTT.Resource
	      ) : CnxnCtxtLeaf[String,String,String] with Factual = {
		tweet(
		  "In asStoreValue on " + this + " for resource: " + rsrc
		)
		val storageDispatch = 
		  rsrc match {
		    case k : mTT.Continuation => {
		      tweet(
			"Resource " + rsrc + " is a continuation"
		      )
		      continuationStorageType
		    }
		    case _ => {
		      tweet(
			"Resource " + rsrc + " is a value"
		      )
		      valueStorageType
		    }
		  };
		
		tweet(
		  "storageDispatch: " + storageDispatch
		)
		
		val blob =
		  storageDispatch match {
		    case "Base64" => {
		      val baos : ByteArrayOutputStream = new ByteArrayOutputStream()
		      val oos : ObjectOutputStream = new ObjectOutputStream( baos )
		      oos.writeObject( rsrc.asInstanceOf[Serializable] )
		      oos.close()
		      new String( Base64Coder.encode( baos.toByteArray() ) )
		    }
		    case "CnxnCtxtLabel" => {
		      tweet(
			"warning: CnxnCtxtLabel method is using XStream"
		      )
		      toXQSafeJSONBlob( rsrc )		  		  
		    }
		    case "XStream" => {
		      tweet(
			"using XStream method"
		      )
		      
		      toXQSafeJSONBlob( rsrc )
		    }
		    case _ => {
		      throw new Exception( "unexpected value storage type" )
		    }
		  }
		new CnxnCtxtLeaf[String,String,String](
		  Left[String,String]( blob )
		)
	      }
	      
	      def asCacheValue(
		ccl : CnxnCtxtLabel[String,String,String]
	      ) : ConcreteHL.HLExpr = {
		tweet(
		  "converting to cache value"
		)
		ccl match {
		  case CnxnCtxtBranch(
		    "string",
		    CnxnCtxtLeaf( Left( rv ) ) :: Nil
		  ) => {
		    val unBlob =
		      fromXQSafeJSONBlob( rv )
		    
		    unBlob match {
		      case rsrc : mTT.Resource => {
			getGV( rsrc ).getOrElse( ConcreteHL.Bottom )
		      }
		    }
		  }
		  case _ => {
		    //asPatternString( ccl )
		    throw new Exception( "unexpected value form: " + ccl )
		  }
		}
	      }
	      
	      override def asResource(
		key : mTT.GetRequest, // must have the pattern to determine bindings
		value : DBObject
	      ) : emT.PlaceInstance = {
		val ltns =
		  labelToNS.getOrElse(
		    throw new Exception( "must have labelToNS to convert mongo object" )
		  )
		val ttv =
		  textToVar.getOrElse(
		    throw new Exception( "must have textToVar to convert mongo object" )
		  )
		val ttt =
		  textToTag.getOrElse(
		    throw new Exception( "must have textToTag to convert mongo object" )
		  )
		//val ttt = ( x : String ) => x
		
		//val ptn = asPatternString( key )
		//println( "ptn : " + ptn )		
		
		CnxnMongoObjectifier.fromMongoObject( value )( ltns, ttv, ttt ) match {
		  case CnxnCtxtBranch( ns, CnxnCtxtBranch( kNs, k :: Nil ) :: CnxnCtxtBranch( vNs, v :: Nil ) :: Nil ) => {
		    matchMap( key, k ) match {
		      case Some( soln ) => {
			if ( compareNameSpace( ns, kvNameSpace ) ) {
			  emT.PlaceInstance(
			    k,
			    Left[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]](
			      mTT.Ground(
				asCacheValue(
				  new CnxnCtxtBranch[String,String,String](
				    "string",
				    v :: Nil
				  )
				)
			      )
			    ),
			    // BUGBUG -- lgm : why can't the compiler determine
			    // that this cast is not necessary?
			    theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
			  )
			}
			else {
			  if ( compareNameSpace( ns, kvKNameSpace ) ) {
			    val mTT.Continuation( ks ) =
			      asCacheK(
				new CnxnCtxtBranch[String,String,String](
				  "string",
				  v :: Nil
				)
			      )
			    emT.PlaceInstance(
			      k,
			      Right[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]]( 
				ks
			      ),
			      // BUGBUG -- lgm : why can't the compiler determine
			      // that this cast is not necessary?
			      theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
			    )
			  }
			  else {
			    throw new Exception( "unexpected namespace : (" + ns + ")" )
			  }
			}
		      }
		      case None => {
			tweet( "Unexpected matchMap failure: " + key + " " + k )
			throw new Exception( "matchMap failure " + key + " " + k )
		      }
		    }						
		  }
		  case _ => {
		    throw new Exception( "unexpected record format : " + value )
		  }
		}				
	      }
	      
	    }
	    override def asCacheK(
	      ccl : CnxnCtxtLabel[String,String,String]
	    ) : Option[mTT.Continuation] = {
	      tweet(
		"converting to cache continuation stack" + ccl
	      )
	      ccl match {
		case CnxnCtxtBranch(
		  "string",
		  CnxnCtxtLeaf( Left( rv ) ) :: Nil
		) => {
		  val unBlob =
		    continuationStorageType match {
		      case "CnxnCtxtLabel" => {
			// tweet(
			// 		      "warning: CnxnCtxtLabel method is using XStream"
			// 		    )
			fromXQSafeJSONBlob( rv )
		      }
		      case "XStream" => {
			fromXQSafeJSONBlob( rv )
		      }
		      case "Base64" => {
			val data : Array[Byte] = Base64Coder.decode( rv )
			val ois : ObjectInputStream =
			  new ObjectInputStream( new ByteArrayInputStream(  data ) )
			val o : java.lang.Object = ois.readObject();
			ois.close()
			o
		      }
		    }
		  
		  unBlob match {
		    case k : mTT.Resource => {
		      Some( k.asInstanceOf[mTT.Continuation] )
		    }
		    case _ => {
		      throw new Exception(
			(
			  ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			  + "ill-formatted continuation stack blob : " + rv
			  + "\n" 
			  + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			  + "\n"
			  + "unBlob : " + unBlob
			  + "\n"
			  + "unBlob type : " + unBlob
			  + "\n"
			  + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			)
		      )
		    }
		  }
		}
		case _ => {
		  throw new Exception( "ill-formatted continuation stack leaf: " + ccl )
		}
	      }
	    }
	    
	    override def asCacheK(
	      ltns : String => String,
	      ttv : String => String,
	      value : DBObject
	    ) : Option[mTT.Continuation] = {
	      throw new Exception( "shouldn't be calling this version of asCacheK" )
	    }
	    override def persistenceManifest : Option[PersistenceManifest] = {
	      tweet(
		(
		  "AgentKVDB : "
		  + "\nthis: " + this
		  + "\n method : persistenceManifest "
		)
	      )
	      val sid = Some( ( s : String ) => recoverFieldName( s ) )
	      val kvdb = this;
	      Some(
		new StringMongoDBManifest( dfStoreUnitStr, sid, sid, sid ) {
		  override def valueStorageType : String = {
		    kvdb.valueStorageType
		  }
		  override def continuationStorageType : String = {
		    kvdb.continuationStorageType
		  }
		}
	      )
	    }
	    def dfStoreUnitStr : String = mnkrExchange( name )
	  }
	}
	override def ptToPt[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
	  here : URI, there : URI
	)(
	  implicit configFileNameOpt : Option[String] 
	) : AgentKVDBNode[ReqBody,RspBody] = {
	  val node =
	    new AgentKVDBNode[ReqBody,RspBody](
	      mkCache( MURI( here ), configFileNameOpt ),
	      List( MURI( there ) ),
	      None,
	      configFileNameOpt
	    ) {
	      override def mkInnerCache[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse]( 
		here : URI,
		configFileName : Option[String]
	      ) : HashAgentKVDB[ReqBody,RspBody] = {
		tweet(
		  (
		    "AgentKVDBNode : "
		    + "\nthis: " + this
		    + "\n method : mkInnerCache "
		    + "\n here: " + here
		    + "\n configFileName: " + configFileName
		  )
		)
		new HashAgentKVDB[ReqBody, RspBody](
		  MURI( here ),
		  configFileName
		) with Blobify with AMQPMonikerOps {		
		  class StringMongoDBManifest(
		    override val storeUnitStr : String,
		    @transient override val labelToNS : Option[String => String],
		    @transient override val textToVar : Option[String => String],
		    @transient override val textToTag : Option[String => String]
		  )
		  extends MongoDBManifest( /* database */ ) {
		    override def valueStorageType : String = {
		      throw new Exception( "valueStorageType not overriden in instantiation" )
		    }
		    override def continuationStorageType : String = {
		      throw new Exception( "continuationStorageType not overriden in instantiation" )
		    }
		    
		    override def storeUnitStr[Src,Label,Trgt]( cnxn : Cnxn[Src,Label,Trgt] ) : String = {     
		      cnxn match {
			case CCnxn( s, l, t ) => s.toString + l.toString + t.toString
			case acT.AgentCnxn( s, l, t ) => s.getHost + l.toString + t.getHost
		      }	    
		    }	
		    
		    def kvNameSpace : String = "record"
		    def kvKNameSpace : String = "kRecord"
		    
		    def compareNameSpace( ns1 : String, ns2 : String ) : Boolean = {
		      ns1.equals( ns2 )
		    }
		    
		    override def asStoreValue(
		      rsrc : mTT.Resource
		    ) : CnxnCtxtLeaf[String,String,String] with Factual = {
		      tweet(
			"In asStoreValue on " + this + " for resource: " + rsrc
		      )
		      val storageDispatch = 
			rsrc match {
			  case k : mTT.Continuation => {
			    tweet(
			      "Resource " + rsrc + " is a continuation"
			    )
			    continuationStorageType
			  }
			  case _ => {
			    tweet(
			      "Resource " + rsrc + " is a value"
			    )
			    valueStorageType
			  }
			};
		      
		      tweet(
			"storageDispatch: " + storageDispatch
		      )
		      
		      val blob =
			storageDispatch match {
			  case "Base64" => {
			    val baos : ByteArrayOutputStream = new ByteArrayOutputStream()
			    val oos : ObjectOutputStream = new ObjectOutputStream( baos )
			    oos.writeObject( rsrc.asInstanceOf[Serializable] )
			    oos.close()
			    new String( Base64Coder.encode( baos.toByteArray() ) )
			  }
			  case "CnxnCtxtLabel" => {
			    tweet(
			      "warning: CnxnCtxtLabel method is using XStream"
			    )
			    toXQSafeJSONBlob( rsrc )		  		  
			  }
			  case "XStream" => {
			    tweet(
			      "using XStream method"
			    )
			    
			    toXQSafeJSONBlob( rsrc )
			  }
			  case _ => {
			    throw new Exception( "unexpected value storage type" )
			  }
			}
		      new CnxnCtxtLeaf[String,String,String](
			Left[String,String]( blob )
		      )
		    }
		    
		    def asCacheValue(
		      ccl : CnxnCtxtLabel[String,String,String]
		    ) : ConcreteHL.HLExpr = {
		      tweet(
			"converting to cache value"
		      )
		      ccl match {
			case CnxnCtxtBranch(
			  "string",
			  CnxnCtxtLeaf( Left( rv ) ) :: Nil
			) => {
			  val unBlob =
			    fromXQSafeJSONBlob( rv )
			  
			  unBlob match {
			    case rsrc : mTT.Resource => {
			      getGV( rsrc ).getOrElse( ConcreteHL.Bottom )
			    }
			  }
			}
			case _ => {
			  //asPatternString( ccl )
			  throw new Exception( "unexpected value form: " + ccl )
			}
		      }
		    }
		    
		    override def asResource(
		      key : mTT.GetRequest, // must have the pattern to determine bindings
		      value : DBObject
		    ) : emT.PlaceInstance = {
		      val ltns =
			labelToNS.getOrElse(
			  throw new Exception( "must have labelToNS to convert mongo object" )
			)
		      val ttv =
			textToVar.getOrElse(
			  throw new Exception( "must have textToVar to convert mongo object" )
			)
		      val ttt =
			textToTag.getOrElse(
			  throw new Exception( "must have textToTag to convert mongo object" )
			)
		      //val ttt = ( x : String ) => x
		      
		      //val ptn = asPatternString( key )
		      //println( "ptn : " + ptn )		
		      
		      CnxnMongoObjectifier.fromMongoObject( value )( ltns, ttv, ttt ) match {
			case CnxnCtxtBranch( ns, CnxnCtxtBranch( kNs, k :: Nil ) :: CnxnCtxtBranch( vNs, v :: Nil ) :: Nil ) => {
			  matchMap( key, k ) match {
			    case Some( soln ) => {
			      if ( compareNameSpace( ns, kvNameSpace ) ) {
				emT.PlaceInstance(
				  k,
				  Left[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]](
				    mTT.Ground(
				      asCacheValue(
					new CnxnCtxtBranch[String,String,String](
					  "string",
					  v :: Nil
					)
				      )
				    )
				  ),
				  // BUGBUG -- lgm : why can't the compiler determine
				  // that this cast is not necessary?
				  theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				)
			      }
			      else {
				if ( compareNameSpace( ns, kvKNameSpace ) ) {
				  val mTT.Continuation( ks ) =
				    asCacheK(
				      new CnxnCtxtBranch[String,String,String](
					"string",
					v :: Nil
				      )
				    )
				  emT.PlaceInstance(
				    k,
				    Right[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]]( 
				      ks
				    ),
				    // BUGBUG -- lgm : why can't the compiler determine
				    // that this cast is not necessary?
				    theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				  )
				}
				else {
				  throw new Exception( "unexpected namespace : (" + ns + ")" )
				}
			      }
			    }
			    case None => {
			      tweet( "Unexpected matchMap failure: " + key + " " + k )
			      throw new Exception( "matchMap failure " + key + " " + k )
			    }
			  }						
			}
			case _ => {
			  throw new Exception( "unexpected record format : " + value )
			}
		      }
		    }
		    
		  }
		  override def asCacheK(
		    ccl : CnxnCtxtLabel[String,String,String]
		  ) : Option[mTT.Continuation] = {
		    tweet(
		      "converting to cache continuation stack" + ccl
		    )
		    ccl match {
		      case CnxnCtxtBranch(
			"string",
			CnxnCtxtLeaf( Left( rv ) ) :: Nil
		      ) => {
			val unBlob =
			  continuationStorageType match {
			    case "CnxnCtxtLabel" => {
			      // tweet(
			      // 		      "warning: CnxnCtxtLabel method is using XStream"
			      // 		    )
			      fromXQSafeJSONBlob( rv )
			    }
			    case "XStream" => {
			      fromXQSafeJSONBlob( rv )
			    }
			    case "Base64" => {
			      val data : Array[Byte] = Base64Coder.decode( rv )
			      val ois : ObjectInputStream =
				new ObjectInputStream( new ByteArrayInputStream(  data ) )
			      val o : java.lang.Object = ois.readObject();
			      ois.close()
			      o
			    }
			  }
			
			unBlob match {
			  case k : mTT.Resource => {
			    Some( k.asInstanceOf[mTT.Continuation] )
			  }
			  case _ => {
			    throw new Exception(
			      (
				">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "ill-formatted continuation stack blob : " + rv
				+ "\n" 
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "\n"
				+ "unBlob : " + unBlob
				+ "\n"
				+ "unBlob type : " + unBlob
				+ "\n"
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			      )
			    )
			  }
			}
		      }
		      case _ => {
			throw new Exception( "ill-formatted continuation stack leaf: " + ccl )
		      }
		    }
		  }
		  
		  override def asCacheK(
		    ltns : String => String,
		    ttv : String => String,
		    value : DBObject
		  ) : Option[mTT.Continuation] = {
		    throw new Exception( "shouldn't be calling this version of asCacheK" )
		  }

		  override def persistenceManifest : Option[PersistenceManifest] = {
		    tweet(
		      (
			"HashAgentKVDB : "
			+ "\nthis: " + this
			+ "\n method : persistenceManifest "
		      )
		    )
		    val sid = Some( ( s : String ) => recoverFieldName( s ) )
		    val kvdb = this;
		    Some(
		      new StringMongoDBManifest( dfStoreUnitStr, sid, sid, sid ) {
			override def valueStorageType : String = {
			  kvdb.valueStorageType
			}
			override def continuationStorageType : String = {
			  kvdb.continuationStorageType
			}
		      }
		    )
		  }
		  def dfStoreUnitStr : String = mnkrExchange( name )
		}
	      }
	    }
	  spawn {
	    node.dispatchDMsgs()
	  }
	  node
	}
	override def ptToMany[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
	  here : URI, there : List[URI]
	)(
	  implicit configFileNameOpt : Option[String]
	) : AgentKVDBNode[ReqBody,RspBody] = {
	  val node =
	    new AgentKVDBNode[ReqBody,RspBody](
	      mkCache( MURI( here ), configFileNameOpt ),
	      there.map( MURI( _ ) ),
	      None,
	      configFileNameOpt
	    ) {
	      override def mkInnerCache[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse]( 
		here : URI,
		configFileName : Option[String]
	      ) : HashAgentKVDB[ReqBody,RspBody] = {
		tweet(
		  (
		    "AgentKVDBNode : "
		    + "\nthis: " + this
		    + "\n method : mkInnerCache "
		    + "\n here: " + here
		    + "\n configFileName: " + configFileName
		  )
		)
		new HashAgentKVDB[ReqBody, RspBody](
		  MURI( here ),
		  configFileName
		) with Blobify with AMQPMonikerOps {		
		  class StringMongoDBManifest(
		    override val storeUnitStr : String,
		    @transient override val labelToNS : Option[String => String],
		    @transient override val textToVar : Option[String => String],
		    @transient override val textToTag : Option[String => String]
		  )
		  extends MongoDBManifest( /* database */ ) {
		    override def valueStorageType : String = {
		      throw new Exception( "valueStorageType not overriden in instantiation" )
		    }
		    override def continuationStorageType : String = {
		      throw new Exception( "continuationStorageType not overriden in instantiation" )
		    }
		    
		    override def storeUnitStr[Src,Label,Trgt]( cnxn : Cnxn[Src,Label,Trgt] ) : String = {     
		      cnxn match {
			case CCnxn( s, l, t ) => s.toString + l.toString + t.toString
			case acT.AgentCnxn( s, l, t ) => s.getHost + l.toString + t.getHost
		      }	    
		    }	
		    
		    def kvNameSpace : String = "record"
		    def kvKNameSpace : String = "kRecord"
		    
		    def compareNameSpace( ns1 : String, ns2 : String ) : Boolean = {
		      ns1.equals( ns2 )
		    }
		    
		    override def asStoreValue(
		      rsrc : mTT.Resource
		    ) : CnxnCtxtLeaf[String,String,String] with Factual = {
		      tweet(
			"In asStoreValue on " + this + " for resource: " + rsrc
		      )
		      val storageDispatch = 
			rsrc match {
			  case k : mTT.Continuation => {
			    tweet(
			      "Resource " + rsrc + " is a continuation"
			    )
			    continuationStorageType
			  }
			  case _ => {
			    tweet(
			      "Resource " + rsrc + " is a value"
			    )
			    valueStorageType
			  }
			};
		      
		      tweet(
			"storageDispatch: " + storageDispatch
		      )
		      
		      val blob =
			storageDispatch match {
			  case "Base64" => {
			    val baos : ByteArrayOutputStream = new ByteArrayOutputStream()
			    val oos : ObjectOutputStream = new ObjectOutputStream( baos )
			    oos.writeObject( rsrc.asInstanceOf[Serializable] )
			    oos.close()
			    new String( Base64Coder.encode( baos.toByteArray() ) )
			  }
			  case "CnxnCtxtLabel" => {
			    tweet(
			      "warning: CnxnCtxtLabel method is using XStream"
			    )
			    toXQSafeJSONBlob( rsrc )		  		  
			  }
			  case "XStream" => {
			    tweet(
			      "using XStream method"
			    )
			    
			    toXQSafeJSONBlob( rsrc )
			  }
			  case _ => {
			    throw new Exception( "unexpected value storage type" )
			  }
			}
		      new CnxnCtxtLeaf[String,String,String](
			Left[String,String]( blob )
		      )
		    }
		    
		    def asCacheValue(
		      ccl : CnxnCtxtLabel[String,String,String]
		    ) : ConcreteHL.HLExpr = {
		      tweet(
			"converting to cache value"
		      )
		      ccl match {
			case CnxnCtxtBranch(
			  "string",
			  CnxnCtxtLeaf( Left( rv ) ) :: Nil
			) => {
			  val unBlob =
			    fromXQSafeJSONBlob( rv )
			  
			  unBlob match {
			    case rsrc : mTT.Resource => {
			      getGV( rsrc ).getOrElse( ConcreteHL.Bottom )
			    }
			  }
			}
			case _ => {
			  //asPatternString( ccl )
			  throw new Exception( "unexpected value form: " + ccl )
			}
		      }
		    }
		    
		    override def asResource(
		      key : mTT.GetRequest, // must have the pattern to determine bindings
		      value : DBObject
		    ) : emT.PlaceInstance = {
		      val ltns =
			labelToNS.getOrElse(
			  throw new Exception( "must have labelToNS to convert mongo object" )
			)
		      val ttv =
			textToVar.getOrElse(
			  throw new Exception( "must have textToVar to convert mongo object" )
			)
		      val ttt =
			textToTag.getOrElse(
			  throw new Exception( "must have textToTag to convert mongo object" )
			)
		      //val ttt = ( x : String ) => x
		      
		      //val ptn = asPatternString( key )
		      //println( "ptn : " + ptn )		
		      
		      CnxnMongoObjectifier.fromMongoObject( value )( ltns, ttv, ttt ) match {
			case CnxnCtxtBranch( ns, CnxnCtxtBranch( kNs, k :: Nil ) :: CnxnCtxtBranch( vNs, v :: Nil ) :: Nil ) => {
			  matchMap( key, k ) match {
			    case Some( soln ) => {
			      if ( compareNameSpace( ns, kvNameSpace ) ) {
				emT.PlaceInstance(
				  k,
				  Left[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]](
				    mTT.Ground(
				      asCacheValue(
					new CnxnCtxtBranch[String,String,String](
					  "string",
					  v :: Nil
					)
				      )
				    )
				  ),
				  // BUGBUG -- lgm : why can't the compiler determine
				  // that this cast is not necessary?
				  theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				)
			      }
			      else {
				if ( compareNameSpace( ns, kvKNameSpace ) ) {
				  val mTT.Continuation( ks ) =
				    asCacheK(
				      new CnxnCtxtBranch[String,String,String](
					"string",
					v :: Nil
				      )
				    )
				  emT.PlaceInstance(
				    k,
				    Right[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]]( 
				      ks
				    ),
				    // BUGBUG -- lgm : why can't the compiler determine
				    // that this cast is not necessary?
				    theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				  )
				}
				else {
				  throw new Exception( "unexpected namespace : (" + ns + ")" )
				}
			      }
			    }
			    case None => {
			      tweet( "Unexpected matchMap failure: " + key + " " + k )
			      throw new Exception( "matchMap failure " + key + " " + k )
			    }
			  }						
			}
			case _ => {
			  throw new Exception( "unexpected record format : " + value )
			}
		      }
		    }
		    
		  }
		  override def asCacheK(
		    ccl : CnxnCtxtLabel[String,String,String]
		  ) : Option[mTT.Continuation] = {
		    tweet(
		      "converting to cache continuation stack" + ccl
		    )
		    ccl match {
		      case CnxnCtxtBranch(
			"string",
			CnxnCtxtLeaf( Left( rv ) ) :: Nil
		      ) => {
			val unBlob =
			  continuationStorageType match {
			    case "CnxnCtxtLabel" => {
			      // tweet(
			      // 		      "warning: CnxnCtxtLabel method is using XStream"
			      // 		    )
			      fromXQSafeJSONBlob( rv )
			    }
			    case "XStream" => {
			      fromXQSafeJSONBlob( rv )
			    }
			    case "Base64" => {
			      val data : Array[Byte] = Base64Coder.decode( rv )
			      val ois : ObjectInputStream =
				new ObjectInputStream( new ByteArrayInputStream(  data ) )
			      val o : java.lang.Object = ois.readObject();
			      ois.close()
			      o
			    }
			  }
			
			unBlob match {
			  case k : mTT.Resource => {
			    Some( k.asInstanceOf[mTT.Continuation] )
			  }
			  case _ => {
			    throw new Exception(
			      (
				">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "ill-formatted continuation stack blob : " + rv
				+ "\n" 
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "\n"
				+ "unBlob : " + unBlob
				+ "\n"
				+ "unBlob type : " + unBlob
				+ "\n"
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			      )
			    )
			  }
			}
		      }
		      case _ => {
			throw new Exception( "ill-formatted continuation stack leaf: " + ccl )
		      }
		    }
		  }
		  
		  override def asCacheK(
		    ltns : String => String,
		    ttv : String => String,
		    value : DBObject
		  ) : Option[mTT.Continuation] = {
		    throw new Exception( "shouldn't be calling this version of asCacheK" )
		  }
		  override def persistenceManifest : Option[PersistenceManifest] = {
		    tweet(
		      (
			"HashAgentKVDB : "
			+ "\nthis: " + this
			+ "\n method : persistenceManifest "
		      )
		    )
		    val sid = Some( ( s : String ) => recoverFieldName( s ) )
		    val kvdb = this;
		    Some(
		      new StringMongoDBManifest( dfStoreUnitStr, sid, sid, sid ) {
			override def valueStorageType : String = {
			  kvdb.valueStorageType
			}
			override def continuationStorageType : String = {
			  kvdb.continuationStorageType
			}
		      }
		    )
		  }
		  def dfStoreUnitStr : String = mnkrExchange( name )
		}
	      }
	    }
	  spawn {
	    println( "initiating dispatch on " + node )
	    node.dispatchDMsgs()
	  }
	  node
	}
	def loopBack[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
	  here : URI
	)(
	  implicit configFileNameOpt : Option[String]
	) : AgentKVDBNode[ReqBody,RspBody] = {
	  val exchange = uriExchange( here )
	  val hereNow =
	    new URI(
	      here.getScheme,
	      here.getUserInfo,
	      here.getHost,
	      here.getPort,
	      "/" + exchange + "Local",
	      here.getQuery,
	      here.getFragment
	    )
	  val thereNow =
	    new URI(
	      here.getScheme,
	      here.getUserInfo,
	      here.getHost,
	      here.getPort,
	      "/" + exchange + "Remote",
	      here.getQuery,
	      here.getFragment
	    )	    
	  
	  val node =
	    new AgentKVDBNode[ReqBody, RspBody](
	      mkCache( MURI( hereNow ), configFileNameOpt ),
	      List( MURI( thereNow ) ),
	      None,
	      configFileNameOpt
	    ) {
	      override def mkInnerCache[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse]( 
		here : URI,
		configFileName : Option[String]
	      ) : HashAgentKVDB[ReqBody,RspBody] = {
		tweet(
		  (
		    "AgentKVDBNode : "
		    + "\nthis: " + this
		    + "\n method : mkInnerCache "
		    + "\n here: " + here
		    + "\n configFileName: " + configFileName
		  )
		)
		new HashAgentKVDB[ReqBody, RspBody](
		  MURI( here ),
		  configFileName
		) with Blobify with AMQPMonikerOps {		
		  class StringMongoDBManifest(
		    override val storeUnitStr : String,
		    @transient override val labelToNS : Option[String => String],
		    @transient override val textToVar : Option[String => String],
		    @transient override val textToTag : Option[String => String]
		  )
		  extends MongoDBManifest( /* database */ ) {
		    override def valueStorageType : String = {
		      throw new Exception( "valueStorageType not overriden in instantiation" )
		    }
		    override def continuationStorageType : String = {
		      throw new Exception( "continuationStorageType not overriden in instantiation" )
		    }
		    
		    override def storeUnitStr[Src,Label,Trgt]( cnxn : Cnxn[Src,Label,Trgt] ) : String = {     
		      cnxn match {
			case CCnxn( s, l, t ) => s.toString + l.toString + t.toString
			case acT.AgentCnxn( s, l, t ) => s.getHost + l.toString + t.getHost
		      }	    
		    }	
		    
		    def kvNameSpace : String = "record"
		    def kvKNameSpace : String = "kRecord"
		    
		    def compareNameSpace( ns1 : String, ns2 : String ) : Boolean = {
		      ns1.equals( ns2 )
		    }
		    
		    override def asStoreValue(
		      rsrc : mTT.Resource
		    ) : CnxnCtxtLeaf[String,String,String] with Factual = {
		      tweet(
			"In asStoreValue on " + this + " for resource: " + rsrc
		      )
		      val storageDispatch = 
			rsrc match {
			  case k : mTT.Continuation => {
			    tweet(
			      "Resource " + rsrc + " is a continuation"
			    )
			    continuationStorageType
			  }
			  case _ => {
			    tweet(
			      "Resource " + rsrc + " is a value"
			    )
			    valueStorageType
			  }
			};
		      
		      tweet(
			"storageDispatch: " + storageDispatch
		      )
		      
		      val blob =
			storageDispatch match {
			  case "Base64" => {
			    val baos : ByteArrayOutputStream = new ByteArrayOutputStream()
			    val oos : ObjectOutputStream = new ObjectOutputStream( baos )
			    oos.writeObject( rsrc.asInstanceOf[Serializable] )
			    oos.close()
			    new String( Base64Coder.encode( baos.toByteArray() ) )
			  }
			  case "CnxnCtxtLabel" => {
			    tweet(
			      "warning: CnxnCtxtLabel method is using XStream"
			    )
			    toXQSafeJSONBlob( rsrc )		  		  
			  }
			  case "XStream" => {
			    tweet(
			      "using XStream method"
			    )
			    
			    toXQSafeJSONBlob( rsrc )
			  }
			  case _ => {
			    throw new Exception( "unexpected value storage type" )
			  }
			}
		      new CnxnCtxtLeaf[String,String,String](
			Left[String,String]( blob )
		      )
		    }
		    
		    def asCacheValue(
		      ccl : CnxnCtxtLabel[String,String,String]
		    ) : ConcreteHL.HLExpr = {
		      tweet(
			"converting to cache value"
		      )
		      ccl match {
			case CnxnCtxtBranch(
			  "string",
			  CnxnCtxtLeaf( Left( rv ) ) :: Nil
			) => {
			  val unBlob =
			    fromXQSafeJSONBlob( rv )
			  
			  unBlob match {
			    case rsrc : mTT.Resource => {
			      getGV( rsrc ).getOrElse( ConcreteHL.Bottom )
			    }
			  }
			}
			case _ => {
			  //asPatternString( ccl )
			  throw new Exception( "unexpected value form: " + ccl )
			}
		      }
		    }
		    
		    override def asResource(
		      key : mTT.GetRequest, // must have the pattern to determine bindings
		      value : DBObject
		    ) : emT.PlaceInstance = {
		      val ltns =
			labelToNS.getOrElse(
			  throw new Exception( "must have labelToNS to convert mongo object" )
			)
		      val ttv =
			textToVar.getOrElse(
			  throw new Exception( "must have textToVar to convert mongo object" )
			)
		      val ttt =
			textToTag.getOrElse(
			  throw new Exception( "must have textToTag to convert mongo object" )
			)
		      //val ttt = ( x : String ) => x
		      
		      //val ptn = asPatternString( key )
		      //println( "ptn : " + ptn )		
		      
		      CnxnMongoObjectifier.fromMongoObject( value )( ltns, ttv, ttt ) match {
			case CnxnCtxtBranch( ns, CnxnCtxtBranch( kNs, k :: Nil ) :: CnxnCtxtBranch( vNs, v :: Nil ) :: Nil ) => {
			  matchMap( key, k ) match {
			    case Some( soln ) => {
			      if ( compareNameSpace( ns, kvNameSpace ) ) {
				emT.PlaceInstance(
				  k,
				  Left[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]](
				    mTT.Ground(
				      asCacheValue(
					new CnxnCtxtBranch[String,String,String](
					  "string",
					  v :: Nil
					)
				      )
				    )
				  ),
				  // BUGBUG -- lgm : why can't the compiler determine
				  // that this cast is not necessary?
				  theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				)
			      }
			      else {
				if ( compareNameSpace( ns, kvKNameSpace ) ) {
				  val mTT.Continuation( ks ) =
				    asCacheK(
				      new CnxnCtxtBranch[String,String,String](
					"string",
					v :: Nil
				      )
				    )
				  emT.PlaceInstance(
				    k,
				    Right[mTT.Resource,List[Option[mTT.Resource] => Unit @suspendable]]( 
				      ks
				    ),
				    // BUGBUG -- lgm : why can't the compiler determine
				    // that this cast is not necessary?
				    theEMTypes.PrologSubstitution( soln ).asInstanceOf[emT.Substitution]
				  )
				}
				else {
				  throw new Exception( "unexpected namespace : (" + ns + ")" )
				}
			      }
			    }
			    case None => {
			      tweet( "Unexpected matchMap failure: " + key + " " + k )
			      throw new Exception( "matchMap failure " + key + " " + k )
			    }
			  }						
			}
			case _ => {
			  throw new Exception( "unexpected record format : " + value )
			}
		      }
		    }
		    
		  }
		  override def asCacheK(
		    ccl : CnxnCtxtLabel[String,String,String]
		  ) : Option[mTT.Continuation] = {
		    tweet(
		      "converting to cache continuation stack" + ccl
		    )
		    ccl match {
		      case CnxnCtxtBranch(
			"string",
			CnxnCtxtLeaf( Left( rv ) ) :: Nil
		      ) => {
			val unBlob =
			  continuationStorageType match {
			    case "CnxnCtxtLabel" => {
			      // tweet(
			      // 		      "warning: CnxnCtxtLabel method is using XStream"
			      // 		    )
			      fromXQSafeJSONBlob( rv )
			    }
			    case "XStream" => {
			      fromXQSafeJSONBlob( rv )
			    }
			    case "Base64" => {
			      val data : Array[Byte] = Base64Coder.decode( rv )
			      val ois : ObjectInputStream =
				new ObjectInputStream( new ByteArrayInputStream(  data ) )
			      val o : java.lang.Object = ois.readObject();
			      ois.close()
			      o
			    }
			  }
			
			unBlob match {
			  case k : mTT.Resource => {
			    Some( k.asInstanceOf[mTT.Continuation] )
			  }
			  case _ => {
			    throw new Exception(
			      (
				">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "ill-formatted continuation stack blob : " + rv
				+ "\n" 
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
				+ "\n"
				+ "unBlob : " + unBlob
				+ "\n"
				+ "unBlob type : " + unBlob
				+ "\n"
				+ ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
			      )
			    )
			  }
			}
		      }
		      case _ => {
			throw new Exception( "ill-formatted continuation stack leaf: " + ccl )
		      }
		    }
		  }
		  
		  override def asCacheK(
		    ltns : String => String,
		    ttv : String => String,
		    value : DBObject
		  ) : Option[mTT.Continuation] = {
		    throw new Exception( "shouldn't be calling this version of asCacheK" )
		  }
		  override def persistenceManifest : Option[PersistenceManifest] = {
		    tweet(
		      (
			"HashAgentKVDB : "
			+ "\nthis: " + this
			+ "\n method : persistenceManifest "
		      )
		    )
		    val sid = Some( ( s : String ) => recoverFieldName( s ) )
		    val kvdb = this;
		    Some(
		      new StringMongoDBManifest( dfStoreUnitStr, sid, sid, sid ) {
			override def valueStorageType : String = {
			  kvdb.valueStorageType
			}
			override def continuationStorageType : String = {
			  kvdb.continuationStorageType
			}
		      }
		    )
		  }
		  def dfStoreUnitStr : String = mnkrExchange( name )
		}
	      }
	    }
	  spawn {
	    println( "initiating dispatch on " + node )
	    node.dispatchDMsgs()
	  }
	  node
	}
      }
    }

  }

  object DieselConfigurationDefaults {
    val localHost : String = "localhost"
    val localPort : Int = 5672
    val remoteHost : String = "localhost"
    val remotePort : Int = 5672
    val dataLocation : String = "/cnxnTestProtocol"    
  }

  trait DieselManufactureConfiguration extends ConfigurationTrampoline {
    def localHost : String =
      configurationFromFile.get( "localHost" ).getOrElse( bail() )
    def localPort : Int =
      configurationFromFile.get( "localPort" ).getOrElse( bail() ).toInt
    def remoteHost : String =
      configurationFromFile.get( "remoteHost" ).getOrElse( bail() )
    def remotePort : Int =
      configurationFromFile.get( "remotePort" ).getOrElse( bail() ).toInt    
    def dataLocation : String = 
      configurationFromFile.get( "dataLocation" ).getOrElse( bail() )
  }

  class DieselEngine( override val configFileName : Option[String] )
       extends DieselManufactureConfiguration with Serializable {	 
    import DieselEngineScope._
    import Being._
    import AgentKVDBNodeFactory._

    import CnxnConversionStringScope._

    import com.protegra_ati.agentservices.store.extensions.StringExtensions._

    val version = "0.0.1"

    override def configurationDefaults : ConfigurationDefaults = {
      DieselConfigurationDefaults.asInstanceOf[ConfigurationDefaults]
    }

    val cnxnGlobal = new acT.AgentCnxn("Global".toURI, "", "Global".toURI)

    def setup[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
      dataLocation : String,
      localHost : String, localPort : Int,
      remoteHost : String, remotePort : Int
    )(
      implicit returnTwist : Boolean
    ) : Either[Being.AgentKVDBNode[ReqBody,RspBody],(Being.AgentKVDBNode[ReqBody, RspBody],Being.AgentKVDBNode[ReqBody, RspBody])] = {
      val ( localExchange, remoteExchange ) = 
	if ( localHost.equals( remoteHost ) && ( localPort == remotePort ) ) {
	  ( dataLocation, dataLocation + "Remote" )	  
	}
	else {
	  ( dataLocation, dataLocation )	  
	}

      if ( returnTwist ) {
	Right[Being.AgentKVDBNode[ReqBody,RspBody],(Being.AgentKVDBNode[ReqBody, RspBody],Being.AgentKVDBNode[ReqBody, RspBody])](
	  (
	    ptToPt[ReqBody, RspBody](
	      new URI( "agent", null, localHost, localPort, localExchange, null, null ),
	      new URI( "agent", null, remoteHost, remotePort, remoteExchange, null, null )
	    ),
	    ptToPt[ReqBody, RspBody](	      
	      new URI( "agent", null, remoteHost, remotePort, remoteExchange, null, null ),
	      new URI( "agent", null, localHost, localPort, localExchange, null, null )
	    )
	  )
	)
      }
      else {
	Left[Being.AgentKVDBNode[ReqBody, RspBody],(Being.AgentKVDBNode[ReqBody, RspBody],Being.AgentKVDBNode[ReqBody, RspBody])](
	  ptToPt(
	    new URI( "agent", null, localHost, localPort, localExchange, null, null ),
	    new URI( "agent", null, remoteHost, remotePort, remoteExchange, null, null )
	  )
	)
      }
    }

    def setup[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
      localHost : String, localPort : Int,
      remoteHost : String, remotePort : Int
    )(
      implicit returnTwist : Boolean
    ) : Either[Being.AgentKVDBNode[ReqBody,RspBody],(Being.AgentKVDBNode[ReqBody, RspBody],Being.AgentKVDBNode[ReqBody, RspBody])] = {
      setup( "/dieselProtocol", localHost, localPort, remoteHost, remotePort )
    }

    def agent[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse]( 
      dataLocation : String
    ) : Being.AgentKVDBNode[ReqBody,RspBody] = {
      val Right( ( client, server ) ) = 
	setup[ReqBody,RspBody](
	  dataLocation, "localhost", 5672, "localhost", 5672
	)( true )
      client
    }	 

    def fileNameToCnxn( fileName : String ) : acT.AgentCnxn = {
      val fileNameRoot = fileName.split( '/' ).last
      new acT.AgentCnxn( fileNameRoot.toURI, "", fileNameRoot.toURI )
    } 

    def evaluateExpression[ReqBody <: PersistedKVDBNodeRequest, RspBody <: PersistedKVDBNodeResponse](
      node : Being.AgentKVDBNode[ReqBody,RspBody]
    )( expr : ConcreteHL.HLExpr )(
      handler : Option[mTT.Resource] => Unit
    ): Unit = {
      expr match {
	case ConcreteHL.Bottom => {
	  throw new Exception( "divergence" )
	}
	case ConcreteHL.FeedExpr( filter, cnxns ) => {
	  for( cnxn <- cnxns ) {
	    val agntCnxn : acT.AgentCnxn =
	      new acT.AgentCnxn( cnxn.src, cnxn.label.toString, cnxn.trgt )
	    reset {
	      for( e <- node.subscribe( agntCnxn )( filter ) ) {
		handler( e )
	      }
	    }
	  }
	}
	case ConcreteHL.ScoreExpr( filter, cnxns, staff ) => {
	  for( cnxn <- cnxns ) {
	    val agntCnxn : acT.AgentCnxn =
	      new acT.AgentCnxn( cnxn.src, cnxn.label.toString, cnxn.trgt )
	    reset {
	      for( e <- node.subscribe( agntCnxn )( filter ) ) {
		handler( e )
	      }
	    }
	  }
	}
	case ConcreteHL.InsertContent( filter, cnxns, value : String ) => {
	  for( cnxn <- cnxns ) {
	    val agntCnxn : acT.AgentCnxn =
	      new acT.AgentCnxn( cnxn.src, cnxn.label.toString, cnxn.trgt )
	    reset {
	      node.publish( agntCnxn )( filter, mTT.Ground( ConcreteHL.PostedExpr( value ) ) )
	    }
	  }
	}
      }
    }

    def evalLoop() : Unit = {
      val link = DSLCommLinkCtor.link()
      val erql : CnxnCtxtLabel[String,String,String] =
	DSLCommLinkCtor.ExchangeLabels.evalRequestLabel(
	  "0", "1", "_"
	).getOrElse( 
	  throw new Exception( "error making evalRequestLabel" )
	)
      val node = agent( "/dieselProtocol" )

      val forward : Option[mTT.Resource] => Unit =
	{
	  ( optRsrc : Option[mTT.Resource] ) => {
	    val erspl : CnxnCtxtLabel[String,String,String] =
	      DSLCommLinkCtor.ExchangeLabels.evalResponseLabel(
		"0", "1", "_"
	      ).getOrElse( "unable to make evaResponseLabel" )
	    for( mTT.Ground( v ) <- optRsrc ) {
	      reset {
		link.publish(
		  erspl,
		  DSLCommLink.mTT.Ground( v ) 
		)
	      }
	    }
	  }
	}

      reset { 
	for( e <- link.subscribe( erql ) ) {
	  e match {
	    case Some( DSLCommLink.mTT.Ground( expr ) ) =>
	      evaluateExpression( node )( expr )( forward )
	    case _ => {
	    }
	  }
	}
      }
    }
  }

  object Server extends Serializable {
    lazy val helpMsg = 
      (
	"-help -- this message\n"
	+ "config=<fileName>\n" 
      )
    def processArgs(
      args : Array[String]
    ) : HashMap[String,String] = {
      val map = new HashMap[String,String]()
      for( arg <- args ) {
	val argNVal = arg.split( "=" )
	if ( argNVal.size > 1 ) {
	  ( argNVal( 0 ), argNVal( 1 ) ) match {
	    case ( "config", file ) => {
	      map += ( "config" -> file )
	    }
	  }
	}
	else {
	  arg match {
	    case "-help" => {
	      println( helpMsg )
	    }
	    case _ => {
	      println( "unrecognized arg: " + arg )
	      println( helpMsg )
	    }
	  }	  
	}
      }
      map
    }
    
    def main( args : Array[String] ) {
      val map = processArgs( args )
      val engine = new DieselEngine( map.get( "config" ) )
      val version = engine.version
      println( "*******************************************************" )
      println( "******************** Diesel engine ********************" )
      println( "******************** Version " + version + " ********************" )
      println( "*******************************************************" )
      
      engine.evalLoop()
    }
  }
}
