// -*- mode: Scala;-*- 
// Filename:    UseCaseHelper.scala 
// Authors:     lgm                                                    
// Creation:    Mon May 13 13:57:26 2013 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.biosimilarity.evaluator.distribution

import com.protegra_ati.agentservices.store._
import com.biosimilarity.lift.model.store._

import scala.util.continuations._ 
import scala.collection.mutable.HashMap

import java.util.UUID

trait ChannelGeneration {
  def erql( sessionId : String = UUID.randomUUID().toString ) : ( String, CnxnCtxtLabel[String,String,String] ) = {
    (
      sessionId,
      DSLCommLinkCtor.ExchangeLabels.evalRequestLabel()( sessionId.toString ).getOrElse( 
	throw new Exception( "error making evalRequestLabel" )
      )
    )
  }
  def erspl( sessionId : String = "_" ) : ( String, CnxnCtxtLabel[String,String,String] ) = {
    (
      sessionId,
      DSLCommLinkCtor.ExchangeLabels.evalResponseLabel()( sessionId ).getOrElse( 
	throw new Exception( "error making evalRequestLabel" )
      )
    )
  }
}

trait MessageGeneration extends CnxnString[String,String,String] {
  import com.protegra_ati.agentservices.store.extensions.StringExtensions._

  def defaultLabelStr = "myLife( inTheBush( ofGhosts( true ) ) )"
  def mkFeedExpr( labelStr : String = defaultLabelStr ) : ConcreteHL.FeedExpr = {
    val feedLabelStr : String = labelStr
    val feedLabel =
      fromTermString(
	feedLabelStr
      ).getOrElse(
	throw new Exception( "failed to parse feed label" + feedLabelStr )
      )
    val feedCnxn =
      ConcreteHL.PortableAgentCnxn("Jerry.Seinfeld".toURI, "", "Jerry.Seinfeld".toURI) 

    ConcreteHL.FeedExpr( feedLabel, List( feedCnxn ) )      
  }
  def mkScoreExpr( labelStr : String = defaultLabelStr ) : ConcreteHL.ScoreExpr = {
    val scoreLabelStr : String = labelStr
    val scoreLabel =
      fromTermString(
	scoreLabelStr
      ).getOrElse(
	throw new Exception( "failed to parse score label" + scoreLabelStr )
      )
    val scoreCnxn =
      ConcreteHL.PortableAgentCnxn("Jerry.Seinfeld".toURI, "", "Jerry.Seinfeld".toURI) 

    ConcreteHL.ScoreExpr(
      scoreLabel,
      List( scoreCnxn ),
      Left[Seq[ConcreteHL.Cnxn],Seq[ConcreteHL.Label]]( List( scoreCnxn ) )
    )          
  }
  def mkPostExpr( labelStr : String = defaultLabelStr ) : ConcreteHL.InsertContent[String] = {
    val postLabelStr : String = labelStr
    val postLabel =
      fromTermString( 
	postLabelStr
      ).getOrElse(
	throw new Exception( "failed to parse post label" + postLabelStr )
      )
    val postCnxn =
      ConcreteHL.PortableAgentCnxn("Jerry.Seinfeld".toURI, "", "Jerry.Seinfeld".toURI) 
    ConcreteHL.InsertContent[String](
      postLabel,
      List( postCnxn ),
      "David Byrne"
    )          
  }  

  def tStream[T]( seed : T )( fresh : T => T ) : Stream[T] = {
    lazy val loopStrm : Stream[T] =
      ( List( seed ) ).toStream append ( loopStrm map fresh );
    loopStrm
  }    
  def uuidStream() : Stream[UUID] =
    tStream[UUID]( UUID.randomUUID )(
      {
        ( uuid : UUID ) => {
          UUID.randomUUID
        }
      }
    )

  def randomGroundTerm(
    rndm : scala.util.Random = new scala.util.Random()
  ) : String = {
    val termType = rndm.nextInt( 3 )
    termType match {
      case 0 => ( rndm.nextInt( 2 ) > 0 ).toString
      case 1 => rndm.nextInt( Int.MaxValue ).toString
      //case 2 => rndm.nextInt( Int.MaxValue ).toFloat.toString
      case 2 => "\"" + UUID.randomUUID().toString + "\""
    }
  }

  def randomLabelStr(
    uuidStrm : Stream[UUID] = uuidStream(),
    prefix : String = "label",
    maxBredth : Int = 5,
    maxDepth : Int = 5,
    truncate : Int = 10,
    streamPrefix : Int = 1000
  ) : String = {
    val rndm = new scala.util.Random()
    if ( maxBredth > 0 ) {        
      val bredth = rndm.nextInt( maxBredth ) + 1
      val functorLocation = rndm.nextInt( streamPrefix )
      val functor = prefix + uuidStrm( functorLocation ).toString.replace( "-", "" ).substring( 0, truncate )
      val subterms =
        if ( bredth > 1 ) {
          ( randomLabelStr( uuidStrm, prefix, maxBredth - 1, maxDepth - 1 ).toString /: ( 2 to bredth ) )(
            {
              ( acc, e ) => {
                acc + "," + randomLabelStr( uuidStrm, prefix, maxBredth - 1, maxDepth - 1 ).toString
              }
            }
          )
        }
        else {
          randomLabelStr( uuidStrm, prefix, maxBredth - 1, maxDepth - 1 ).toString
        }
      functor + "(" + subterms + ")"
    } else {
      randomGroundTerm( rndm )
    }
  }

  lazy val uuidStreamStream : Stream[Stream[UUID]] =
      tStream[Stream[UUID]]( uuidStream() )(
        {
          ( uuidStrm : Stream[UUID] ) => {
            uuidStream()
          }
        }
      )    

  lazy val evalRequestLabelStream : Stream[CnxnCtxtLabel[String,String,String]] = {
    uuidStreamStream.take( 1 )( 0 ).map(
      ( uuid : UUID ) => {
        DSLCommLinkCtor.ExchangeLabels.evalRequestLabel()( uuid.toString ).getOrElse( 
          throw new Exception( "error making evalRequestLabel" )
        )
      }
    )
  }
  lazy val selfCnxnStream : Stream[ConcreteHL.PortableAgentCnxn] = {
    uuidStreamStream.take( 2 )( 1 ).map(
      ( uuid : UUID ) => {
        ConcreteHL.PortableAgentCnxn( uuid.toString.toURI, "", uuid.toString.toURI) 
      }
    )
  }
  lazy val randomLabelStream : Stream[CnxnCtxtLabel[String,String,String]] = {
    uuidStreamStream.map(
      {
        ( uuidStrm : Stream[UUID] ) => {
          fromTermString(
            randomLabelStr( uuidStrm )
          ).getOrElse( throw new Exception( "unable to parse label string" ) )
        }
      }
    )
  }
}

object CommManagement {
  import DSLCommLinkCtor._
  @transient
  //lazy val ( client1, server1 ) = stdBiLink()
  var _commLink : Option[StdEvaluationRequestChannel] = None
  def commLink(
    flip : Boolean = false
  ) : StdEvaluationRequestChannel = {
    _commLink match {
      case Some( cLink ) => cLink
      case None => {
	val cLink : StdEvaluationRequestChannel = stdLink()( flip )
	_commLink = Some( cLink )
	cLink
      }
    }
  }
}

trait StorageManagement {
  import CommManagement._
  def doDrop() = {
    import com.biosimilarity.lift.model.store.mongo._
    val clntSess1 =
      MongoClientPool.client( commLink().cache.sessionURIFromConfiguration )
    val mcExecLocal =
      clntSess1.getDB( commLink().cache.defaultDB )( "DSLExecProtocolLocal" )
    val mcExecRemote =
      clntSess1.getDB( commLink().cache.defaultDB )( "DSLExecProtocolRemote" )
    val mcExec =
      clntSess1.getDB( commLink().cache.defaultDB )( "DSLExecProtocol" )

    mcExecLocal.drop
    mcExecRemote.drop
    mcExec.drop
  }
}

trait ExerciseHLDSL {  
  self : ChannelGeneration with MessageGeneration with AgentCnxnTypes =>
  import CommManagement._
  import DSLCommLinkCtor._

  @transient
  val sessionMap =
    new HashMap[String,( Either[ConcreteHL.HLExpr,ConcreteHL.HLExpr], Option[ConcreteHL.HLExpr] )]()

  implicit def toAgentCnxn( pAC : ConcreteHL.PortableAgentCnxn ) : AgentCnxn = {
    new AgentCnxn( pAC.src, pAC.label, pAC.trgt )
  }     
  def doPutBottomRequest( sessionId : String = UUID.randomUUID.toString() ) = {
    val ( _, erqlChan ) = erql( sessionId ) 

    sessionMap += ( sessionId -> ( Left[ConcreteHL.HLExpr,ConcreteHL.HLExpr]( ConcreteHL.Bottom ), None ) )

    reset {      
      commLink().put( erqlChan, DSLCommLink.mTT.Ground( ConcreteHL.Bottom ) )
    }
  }
  def doPutHLExprRequest(
    node : StdEvaluationRequestChannel,
    sessionId : String,
    expr : ConcreteHL.HLExpr
  ) = {
    val ( _, erqlChan ) = erql( sessionId ) 

    sessionMap += ( sessionId -> ( Left[ConcreteHL.HLExpr,ConcreteHL.HLExpr]( expr ), None ) )       

    reset { node.put( erqlChan, DSLCommLink.mTT.Ground( expr ) ) }
  }
  def doPutFeedRequest(
    node : StdEvaluationRequestChannel = commLink(),
    labelStr : String = "myLife( inTheBush( ofGhosts( true ) ) )",
    sessionId : String = UUID.randomUUID.toString()
  ) = {
    doPutHLExprRequest( node, sessionId, mkFeedExpr( labelStr ) )
  }
  def doPutScoreRequest(
    node : StdEvaluationRequestChannel = commLink(),
    labelStr : String = "myLife( inTheBush( ofGhosts( true ) ) )",
    sessionId : String = UUID.randomUUID.toString()
  ) = {
    doPutHLExprRequest( node, sessionId, mkScoreExpr( labelStr ) )
  }
  def doPutPostRequest(
    node : StdEvaluationRequestChannel = commLink(),
    labelStr : String = "myLife( inTheBush( ofGhosts( true ) ) )",
    sessionId : String = UUID.randomUUID.toString()
  ) = {
    doPutHLExprRequest( node, sessionId, mkPostExpr( labelStr ) )
  }

  def doGetRequest( 
    sessionId : String = "SessionId",
    node : StdEvaluationRequestChannel = commLink()
  ) = {
    val ( _, erqlChan ) = erql( sessionId ) 

    reset {
      for( e <- node.subscribe( erqlChan ) ) {
	println( e )
      }
    }
  }
  
  def doGetFeedResponse(
    node : StdEvaluationRequestChannel = commLink(),
    sessionId : String = "SessionId"
  ) = {
    for( eitherExpr <- sessionMap.get( sessionId ) ) {
      eitherExpr match {
	case ( Left( expr@ConcreteHL.FeedExpr( label, cnxns ) ), None ) => {
	  val ( _, ersplChan ) = erspl( sessionId ) 
	  reset {
	    for( e <- node.subscribe( ersplChan ) ) {
	      val rslt = ( Right[ConcreteHL.HLExpr,ConcreteHL.HLExpr]( expr ), None )
	      sessionMap += ( sessionId -> rslt );
	      ()
	    }
	  }
	}
	case ( Left( expr ), _ ) => {
	  throw new Exception( "unexpected expression type: " + expr )
	}
	case ( Right( expr ), _ ) => {
	  println( "session closed" )
	}
      }      
    }
  }
  def doGetScoreResponse(
    node : StdEvaluationRequestChannel = commLink(),
    sessionId : String = "SessionId" 
  ) = {    
    for( eitherExpr <- sessionMap.get( sessionId ) ) {
      eitherExpr match {
	case ( Left( expr@ConcreteHL.ScoreExpr( label, cnxns, staff ) ), None ) => {
	  val ( _, ersplChan ) = erspl( sessionId ) 
	  reset {
	    for( e <- node.subscribe( ersplChan ) ) {
	      val rslt = ( Right[ConcreteHL.HLExpr,ConcreteHL.HLExpr]( expr ), None )
	      sessionMap += ( sessionId -> rslt );
	      ()
	    }
	  }
	}
	case ( Left( expr ), _ ) => {
	  throw new Exception( "unexpected expression type: " + expr )
	}
	case ( Right( expr ), _ ) => {
	  println( "session closed" )
	}
      }      
    }
  }
  def doGetPostResponse(
    node : StdEvaluationRequestChannel = commLink(),
    sessionId : String = "SessionId"
  ) = {
    for( eitherExpr <- sessionMap.get( sessionId ) ) {
      eitherExpr match {
	case ( Left( expr@ConcreteHL.InsertContent( label, cnxns, content ) ), None ) => {
	  val ( _, ersplChan ) = erspl( sessionId ) 
	  reset {
	    for( e <- node.subscribe( ersplChan ) ) {
	      val rslt = ( Right[ConcreteHL.HLExpr,ConcreteHL.HLExpr]( expr ), None )
	      sessionMap += ( sessionId -> rslt );
	      ()
	    }
	  }
	}
	case ( Left( expr ), _ ) => {
	  throw new Exception( "unexpected expression type: " + expr )
	}
	case ( Right( expr ), _ ) => {
	  println( "session closed" )
	}
      }      
    }
  }
}

trait UseCaseHelper extends MessageGeneration
 with ChannelGeneration
 with StorageManagement
 with ExerciseHLDSL
 with AgentCnxnTypes
 with CnxnString[String,String,String] {   
}

package usage {
  object SimpleClient
    extends EvaluationCommsService  
     with MessageGeneration
     with ChannelGeneration
     with EvalConfig
     with DSLCommLinkConfiguration
     with StorageManagement
     with Serializable
  {
    import com.protegra_ati.agentservices.store.extensions.StringExtensions._
  }
  
  object HLDSLProbe
    extends ExerciseHLDSL
     with ChannelGeneration
     with MessageGeneration
     with AgentCnxnTypes
}
