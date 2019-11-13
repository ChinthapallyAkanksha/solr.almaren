package com.github.music.of.the.ainur.almaren.solr

import org.apache.spark.sql.SaveMode
import org.scalatest._
import com.github.music.of.the.ainur.almaren.Almaren
import com.github.music.of.the.ainur.almaren.builder.Core.Implicit
import com.github.music.of.the.ainur.almaren.solr.Solr.SolrImplicit

class Test extends FunSuite with BeforeAndAfter {

  val almaren = Almaren("App Test")

  val spark = almaren.spark
    .master("local[*]")
    .config("spark.ui.enabled","false")
    .config("spark.sql.shuffle.partitions", "1")
    .getOrCreate()
  
  spark.sparkContext.setLogLevel("ERROR")

  import spark.implicits._

  // Create twitter table with data
  val jsonData = bootstrap

  // Save Twitter data to Solr
  val twitter = almaren.builder
    .sourceSql("select id,text from twitter")
    .targetSolr("gettingstarted","localhost:9983",SaveMode.Overwrite,Map("soft_commit_secs" -> "1"))
  almaren.batch(twitter)

  // Read Data From Solr
  val readTwitter = almaren.builder.sourceSolr("gettingstarted","localhost:9983")
  val solrData = almaren.batch(readTwitter)

  // Waiting 10 seconds for Solr commit...
  Thread.sleep(10000) 

  // Test count
  val inputCount = jsonData.count()
  val solrDataCount = solrData.count()

  test("number of records should match") {
    assert(inputCount == solrDataCount)
  }

  // Check if ids match
  val diff = jsonData.as("json").join(solrData.as("solr"), $"json.id" <=> $"solr.id","leftanti").count()
  test("match records") {
    assert(diff == 0)
  }

  def bootstrap = {
    val res = spark.read.json("src/test/resources/sample_data/twitter_search_data.json")
    res.createTempView("twitter")
    res
  }

  after {
    spark.stop
  }
}
