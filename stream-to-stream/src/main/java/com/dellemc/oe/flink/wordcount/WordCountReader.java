/*
 * Copyright (c) 2018 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package com.dellemc.oe.flink.wordcount;

import com.dellemc.oe.serialization.UTF8StringDeserializationSchema;
import com.dellemc.oe.util.CommonParams;
import com.dellemc.oe.util.Constants;
import com.dellemc.oe.util.Utils;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.DefaultCredentials;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.FlinkPravegaWriter;
import io.pravega.connectors.flink.PravegaConfig;
import io.pravega.connectors.flink.PravegaEventRouter;
import io.pravega.connectors.flink.serialization.PravegaSerialization;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/*
 * At a high level, WordCountReader reads from a Pravega stream, and prints
 * the word count summary to the output. This class provides an example for
 * a simple Flink application that reads streaming data from Pravega.
 *
 * And  after flink transformation  output redirect to another pravega stream.
 *
 * This application has the following input parameters
 *     stream - Pravega stream name to read from
 *     controller - the Pravega controller URI, e.g., tcp://localhost:9090
 *                  Note that this parameter is automatically used by the PravegaConfig class
 */
public class WordCountReader {

    // Logger initialization
    private static final Logger LOG = LoggerFactory.getLogger(WordCountReader.class);

    // The application reads data from specified Pravega stream and once every 10 seconds
    // prints the distinct words and counts from the previous 10 seconds.

    public static void main(String[] args) throws Exception {
        LOG.info("Starting WordCountReader...");

        CommonParams.init(args);
        final String scope = CommonParams.getParam(Constants.SCOPE);
        final String streamName = CommonParams.getParam(Constants.STREAM_NAME);
        final URI controllerURI = URI.create(CommonParams.getParam(Constants.CONTROLLER_URI));

        LOG.info("#######################     SCOPE   ###################### " + scope);
        LOG.info("#######################     streamName   ###################### " + streamName);
        LOG.info("#######################     controllerURI   ###################### " + controllerURI);

        // Create client config
        PravegaConfig pravegaConfig = PravegaConfig.fromDefaults()
                    .withControllerURI(controllerURI)
                    .withDefaultScope(scope)
                    .withHostnameValidation(false);
        LOG.info("==============  pravegaConfig  =============== " + pravegaConfig);

        if (CommonParams.isPravegaStandalone()) {
            try (StreamManager streamManager = StreamManager.create(pravegaConfig.getClientConfig())) {
                // create the requested scope (if necessary)
                streamManager.createScope(scope);
            }
        }

        // create the Pravega input stream (if necessary)
        Stream stream = Utils.createStream(
                pravegaConfig,
                streamName);
        LOG.info("==============  stream  =============== " + stream);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // create the Pravega source to read a stream of text
        FlinkPravegaReader<String> source = FlinkPravegaReader.<String>builder()
                .withPravegaConfig(pravegaConfig)
                .forStream(stream)
                .withDeserializationSchema(new UTF8StringDeserializationSchema())
                .build();
        LOG.info("==============  SOURCE  =============== " + source);
        // count each word over a 10 second time period
        DataStream<WordCount> dataStream = env.addSource(source).name(streamName)
                .flatMap(new WordCountReader.Splitter())
                .keyBy("word")
                .timeWindow(Time.seconds(10))
                .sum("count");

        // create an output sink to print to stdout for verification
        dataStream.printToErr();

        LOG.info("==============  PRINTED  ===============");
        Stream output_stream = getOrCreateStream(pravegaConfig, "output-stream", 3);
        // create the Pravega sink to write a stream of text
        FlinkPravegaWriter<WordCount> writer = FlinkPravegaWriter.<WordCount>builder()
                .withPravegaConfig(pravegaConfig)
                .forStream(output_stream)
                .withEventRouter(new EventRouter())
                .withSerializationSchema(PravegaSerialization.serializationFor(WordCount.class))
                .build();
        dataStream.addSink(writer).name("OutputStream");

        // create another output sink to print to stdout for verification

        LOG.info("============== Final output ===============");
        dataStream.printToErr();
        // execute within the Flink environment
        env.execute("WordCountReader");

        LOG.info("Ending WordCountReader...");
    }

    static public Stream getOrCreateStream(PravegaConfig pravegaConfig, String streamName, int numSegments) {
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .scalingPolicy(ScalingPolicy.fixed(numSegments))
                .build();

        return  Utils.createStream(pravegaConfig, streamName, streamConfig);
    }

    /*
     * Event Router class
     */
    public static class EventRouter implements PravegaEventRouter<WordCount> {
        // Ordering - events with the same routing key will always be
        // read in the order they were written
        @Override
        public String getRoutingKey(WordCount event) {
            return "SameRoutingKey";
        }
    }

    // split data into word by space
    private static class Splitter implements FlatMapFunction<String, WordCount> {
        @Override
        public void flatMap(String line, Collector<WordCount> out) throws Exception {
            for (String word : line.split(" ")) {
                out.collect(new WordCount(word, 1));
            }
        }
    }

}
