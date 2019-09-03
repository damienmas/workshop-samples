package io.pravega.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger

object StreamPravegaToConsole {
  def main(args: Array[String]) {
    val spark = SparkSession.builder
      .appName("StreamPravegaToConsole")
      .getOrCreate()

    val scope = sys.env.getOrElse("PRAVEGA_SCOPE", "workshop-samples")
    val controller = sys.env.getOrElse("PRAVEGA_CONTROLLER", "tcp://localhost:9090")

    spark
      .readStream
      .format("pravega")
      .option("controller", controller)
      .option("scope", scope)
      .option("stream", "streamprocessing")
      // If there is no checkpoint, start at the earliest event.
      .option("start_stream_cut", "earliest")
      .load()
      .selectExpr("cast(event as string)", "scope", "stream", "segment_id", "offset")
      .writeStream
      .trigger(Trigger.ProcessingTime(3000))
      .outputMode("append")
      .format("console")
      .option("truncate", "false")
      .option("checkpointLocation", "/tmp/spark-checkpoints-StreamPravegaToConsole")
      .start()
      .awaitTermination()
  }
}
