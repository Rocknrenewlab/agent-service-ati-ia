package com.protegra_ati.agentservices.core.util.cloner

import org.junit.runners.JUnit4
import org.specs2.runner._
import java.util.concurrent.{BrokenBarrierException, CyclicBarrier}
import com.rits.cloning.Cloner
import com.protegra_ati.agentservices.core.schema.{Image, Profile}
import com.protegra_ati.agentservices.core.schema.persistence.CacheableData
import com.protegra_ati.agentservices.store.util.{Reporting, Severity}

class ParallelSingletonClonerPerformanceTest(repetitions: Int)
{
  val newCloner = new Cloner()

  val startTime = System.currentTimeMillis()
  val barrier: CyclicBarrier = new CyclicBarrier(repetitions, new Runnable()
  {
    override def run()
    {
      println("total execution time for " + repetitions + " of parallel clonings with singleton cloner is " + ( System.currentTimeMillis() - startTime ) + "msec");
    }
  });


  def runTest() =
  {
    for ( i <- 0 to this.repetitions ) {
      val t: Thread = new Thread(new CloneWorker())
      t.start()
    }
  }

  private class CloneWorker extends Runnable with Reporting
  {
    override def run()
    {
      val clone = newCloner.deepClone(ClonerPerformanceTestConstants.PROFILE) //newCloner.deepClone(ClonerPerformanceTestConstants.PROFILE)
      if ( clone.toString != ClonerPerformanceTestConstants.PROFILE.toString )
        throw new IllegalThreadStateException("multithreadeed cloning is broken")

      //TODO do some cloning

      try {
        barrier.await();
      } catch {
        case e: Exception => report("Exception occured in serialize method", e, Severity.Error)
      }
    }
  }

}


object ParallelSingletonClonerPerformanceTest
{

  def main(args: Array[ String ]) =
  {
    val parallel = new ParallelSingletonClonerPerformanceTest(10000)
    parallel.runTest()
  }

}


class ParallelMultiInstancesClonerPerformanceTest(repetitions: Int)
{
  val startTime = System.currentTimeMillis()
  val barrier: CyclicBarrier = new CyclicBarrier(repetitions, new Runnable()
  {
    override def run()
    {
      println("total execution time for " + repetitions + " of parallel clonings with cloner per repetition is " + ( System.currentTimeMillis() - startTime ) + "msec");
    }
  });


  def runTest() =
  {

    for ( i <- 0 to this.repetitions ) {
      val t: Thread = new Thread(new CloneWorker())
      t.start()
    }
  }

  private class CloneWorker extends Runnable with Reporting
  {
    override def run()
    {
      val clone = new Cloner().deepClone(ClonerPerformanceTestConstants.PROFILE) // newCloner.shallowClone(ClonerPerformanceTestConstants.PROFILE)
      if ( clone.toString != ClonerPerformanceTestConstants.PROFILE.toString )
        throw new IllegalThreadStateException("multithreadeed cloning is broken")

      //TODO do some cloning

      try {
        barrier.await();
      } catch {
        case e: Exception => report("Exception occured in serialize method", e, Severity.Error)
      }
    }
  }


}

object ParallelMultiInstancesClonerPerformanceTest
{


  def main(args: Array[ String ]) =
  {
    val parallel = new ParallelMultiInstancesClonerPerformanceTest(10000)
    parallel.runTest()
  }


}


class SequentialMultiInstancesClonerPerformanceTest(repetitions: Int)
{
  val startTime = System.currentTimeMillis()


  def runTest() =
  {

    for ( i <- 0 to this.repetitions ) {
      new CloneWorker().run()
    }
    println("total execution time for " + repetitions + " of sequential clonings with cloner per repetition is " + ( System.currentTimeMillis() - startTime ) + "msec");

  }

  private class CloneWorker extends Runnable
  {
    override def run()
    {
      val clone = new Cloner().deepClone(ClonerPerformanceTestConstants.PROFILE) // newCloner.shallowClone(ClonerPerformanceTestConstants.PROFILE)

    }
  }


}

object SequentialMultiInstancesClonerPerformanceTest
{


  def main(args: Array[ String ]) =
  {
    val parallel = new SequentialMultiInstancesClonerPerformanceTest(10000)
    parallel.runTest()
  }


}


class SequentialSingleInstancesClonerPerformanceTest(repetitions: Int)
{
  val startTime = System.currentTimeMillis()
  val cloner =new Cloner()

  def runTest() =
  {

    for ( i <- 0 to this.repetitions ) {
      new CloneWorker().run()
    }
    println("total execution time for " + repetitions + " of sequential clonings with a single cloner is " + ( System.currentTimeMillis() - startTime ) + "msec");

  }

  private class CloneWorker extends Runnable
  {
    override def run()
    {
      val clone = cloner.deepClone(ClonerPerformanceTestConstants.PROFILE) // newCloner.shallowClone(ClonerPerformanceTestConstants.PROFILE)

    }
  }


}

object SequentialSingleInstancesClonerPerformanceTest
{


  def main(args: Array[ String ]) =
  {
    val parallel = new SequentialSingleInstancesClonerPerformanceTest(10000)
    parallel.runTest()
  }


}


object ClonerPerformanceTestConstants
{
  val PROFILE = new Profile("Terry", "Bunio", "test Description", "123456789@gmail.com", "CA", "Manitoba", "Winnipeg", "postalCode", "website", "1234")
  val PROFILE0 = new Profile("test", "test", "test", "temp", "CA", "Manitoba", "Winnipeg", "postalCode", "website", "1234")


}






