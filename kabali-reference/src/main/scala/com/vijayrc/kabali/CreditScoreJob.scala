package com.vijayrc.kabali

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
 * Created by vxr63 on 6/18/16.
 */
class CreditScoreJob(sc: SparkContext) extends Sampling {

  val level = StorageLevel.MEMORY_AND_DISK

  override def kabaliConfig: String = "kabali.yml"

  /**
   * steps
   * -----
   * kabali will extract sample based on tester-configured biased stratified sampling
   * kabali will run only the sample through the batch process
   * kabali will validate the sample batch output through configured thresholds
   * kabali will allow the job to proceed only if no significant deviation is found
   */
  def run(context: SparkContext): Unit = {
    val rdd = sc.emptyRDD[CreditProfile] //read customer profiles data from cassandra | hdfs
    val samplables = rdd.map(item => item.asInstanceOf[Samplable])
    checkAndRun(samplables, context)
  }

  /**
   * run the scoring model for sample set only
   */
  override def runSample(rdd: RDD[Samplable]): RDD[Sampler] = {
    val creditProfiles = rdd.map(r => r.asInstanceOf[CreditProfile])
    applyScoringModel(creditProfiles).map(score => score.asInstanceOf[Sampler])
  }

  /**
   * run the scoring model for entire population
   */
  override def runPopulation(rdd: RDD[Samplable]) = {
    val creditProfiles = rdd.map(r => r.asInstanceOf[CreditProfile])
    applyScoringModel(creditProfiles)
  }

  /**
   * Run the original logic of the job, say you take the rdd through derivation of some attributes and apply a scoring model.
   * @param rdd the rdd of original job items
   * @return the processed job items
   */
  def applyScoringModel(rdd: RDD[CreditProfile]): RDD[CreditScore] = {
    rdd.map(profile => new CreditScore) //some real attribution and scoring model goes here; now dummy shown
  }

}

