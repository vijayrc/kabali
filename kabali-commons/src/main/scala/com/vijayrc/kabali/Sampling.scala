package com.vijayrc.kabali

import java.io.FileReader
import java.net.URL
import java.util.{List => JList}

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.base.Preconditions.checkNotNull
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.{Logging, SparkContext}

import scala.collection.JavaConverters._


/**
  * Created by vxr63 on 6/18/16.
  */
trait Sampling extends Logging {

  /**
    * client must implement to run the sample through the batch process
    * planned for entire population
    */
  def runSample(rdd: RDD[Samplable]): RDD[Sampler]

  /**
    * client must implement to run the population through the batch process
    * planned for entire population
    */
  def runPopulation(rdd: RDD[Samplable])

  /**
    * client must provide the sampling yaml file
    */
  def kabaliConfig: String

  /**
   * Kabali will run and validate sample first before running against whole population
    */
  protected def checkAndRun(population: RDD[Samplable], sc: SparkContext): Unit = {
    val samplables: RDD[Samplable] = extract(population, sc)
    val samplers: RDD[Sampler] = runSample(samplables)

    if (validate(samplers, sc)) {
      logInfo("Job is safe to proceed for entire population")
      runPopulation(population)
    } else {
      logError("Job is exhibiting deviation from acceptable thresholds, hence cannot proceed!")
    }
  }

  /**
    * kabali will extract the samples from population
    */
  private def extract(samplables: RDD[Samplable], sc: SparkContext): RDD[Samplable] = {
    val sqlContext = new SQLContext(sc)
    var samples: RDD[Samplable] = sc.emptyRDD[Samplable]
    val jsons = samplables.map(item => item.getJson)

    //get the fraction of the sample vs population
    val fraction = config.sampleSize.toDouble / jsons.count().toDouble

    //register the samplables as table
    sqlContext.read.json(jsons).cache().registerTempTable(allSamplables)

    //extract each configured strata sample using spark-sql
    config.stratas.foreach(strata => {
      val matched = sqlContext.sql(strata.sql.stripMargin)
      val ids = matched.select(column).map(row => row.getString(0)).collect()
      val strataFraction = strata.fraction * fraction
      logInfo("name=" + strata.name + s"|fraction=$fraction|strata-fraction=$strataFraction")

      val sample = samplables
        .filter(p => ids.contains(p.getKey))
        .sample(withReplacement = false, strataFraction, 0)
      samples = samples.union(sample)
    })
    logInfo("kabali sample size = " + samples.count())
    samples.cache()
    samples
  }

  /**
    * kabali will validate the processed sample against the thresholds
    */
  private def validate(rdd: RDD[Sampler], sc: SparkContext): Boolean = {
    val sqlContext = new SQLContext(sc)
    val jsons = rdd.map(item => item.getKey.replaceAll("[\\s]", "_"))

    //register all the strata samples together as a table
    sqlContext.read.json(jsons).cache().registerTempTable(allSamplers)

    //pick the key used for sample health determination
    val scoreSet = sqlContext.sql(config.scoreKey.stripMargin)
    val column = scoreSet.columns(0)
    val keys = scoreSet.select(column).map(row => row.getLong(0).toDouble).collect()
    val stats = new DescriptiveStatistics(keys)

    val tolerance = config.tolerance
    val expectedMean = config.expectedMean
    val sampleMean = stats.getMean

    //validate the sample health against configured tolerance expectations
    sampleMean < (expectedMean + expectedMean * tolerance) && sampleMean > (expectedMean - expectedMean * tolerance)
  }

  /**
   * load the sampling strata configuration from client file
   * @return
   */
  private def loadFromFile: Config = {
    val resource: URL = this.getClass.getClassLoader.getResource(kabaliConfig)
    val reader = new FileReader(resource.getFile)
    new ObjectMapper(new YAMLFactory()).readValue(reader, classOf[Config])
  }

  override def toString: String = {
    val builder: StringBuilder = new StringBuilder
    builder.append(config.job + "|\n")
    for (strata: Strata <- config.stratas) builder.append(strata.toString)
    builder.toString()
  }

  private lazy val config = loadFromFile
  private val column: String = "_c0"
  private val allSamplables: String = "samplable"
  private val allSamplers: String = "sampler"

}

/**
 * Reads from the configuration, say kabali.yaml file
  */
class Config(@JsonProperty("job") _job: String,
             @JsonProperty("entityKey") _entityKey: String,
             @JsonProperty("scoreKey") _scoreKey: String,
             @JsonProperty("tolerance") _tolerance: String,
             @JsonProperty("sampleSize") _sampleSize: String,
             @JsonProperty("expectedMean") _expectedMean: String,
             @JsonProperty("expectedDeviation") _expectedDeviation: String,
             @JsonProperty("expectedSkewness") _expectedSkewness: String,
             @JsonProperty("expectedVariance") _expectedVariance: String,
             @JsonProperty("stratas") _stratas: JList[Strata]) {

  val job = checkNotNull(_job, "job cannot be null", "")
  val entityKey = checkNotNull(_entityKey, "entityKey name cannot be null", "")
  val scoreKey = checkNotNull(_scoreKey, "scoreKey name cannot be null", "")
  val tolerance = checkNotNull(_tolerance, "tolerance cannot be null", "").toDouble
  val sampleSize = checkNotNull(_sampleSize, "sampleSize cannot be null", "").toLong

  val expectedMean = checkNotNull(_expectedMean, "expected mean cannot be null", "").toDouble
  val expectedDeviation = checkNotNull(_expectedDeviation, "expected deviation cannot be null", "").toDouble
  val expectedSkewness = checkNotNull(_expectedSkewness, "expected skewness cannot be null", "").toDouble
  val expectedVariance = checkNotNull(_expectedVariance, "expected variance cannot be null", "").toDouble

  val stratas = checkNotNull(_stratas, "stratas cannot be null", "").asScala

  override def toString: String = {
    s"$job|$entityKey|" +
      s"tolerance=$tolerance|" +
      s"sampleSize=$sampleSize|" +
      s"expectedDeviation=$expectedDeviation|" +
      s"expectedMean=$expectedMean|" +
      s"expectedSkewness=$expectedSkewness|" +
      s"expectedVariance=$expectedVariance|" +
      s"\n$stratas"
  }
}

/**
  */
class Strata(@JsonProperty("name") _name: String,
             @JsonProperty("fraction") _fraction: String,
             @JsonProperty("sql") _sql: String) {

  val name = checkNotNull(_name, "name cannot be null", "")
  val fraction = checkNotNull(_fraction, "fraction cannot be null", "").toDouble
  val sql = checkNotNull(_sql, "query cannot be null", "")

  override def toString: String = {
    s"$name|$fraction|$sql"
  }
}
