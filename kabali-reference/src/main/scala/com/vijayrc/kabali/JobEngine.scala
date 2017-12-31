package com.vijayrc.kabali

import org.apache.spark.{SparkContext, SparkConf}

/**
  * Created by vxr63 on 6/18/16.
  */
object JobEngine {

  def main(args: Array[String]) {
    val sc = new SparkContext(new SparkConf().setAppName("credit-job").setMaster("spark://localhost:7077"))
    val job = new CreditScoreJob(sc)
    job.run(sc)
  }

}
