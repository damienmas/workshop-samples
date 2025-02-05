/*
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 */
package com.dellemc.oe.readers;

import java.net.URI;

import com.dellemc.oe.model.JSONData;
import com.dellemc.oe.serialization.JsonDeserializationSchema;
import com.dellemc.oe.serialization.UTF8StringDeserializationSchema;
import com.dellemc.oe.util.Constants;
import io.pravega.client.stream.*;
import com.dellemc.oe.util.CommonParams;
import com.dellemc.oe.model.ImageData;
import com.dellemc.oe.serialization.ByteArrayDeserializationSchema;
import com.dellemc.oe.util.Utils;
import io.pravega.connectors.flink.FlinkPravegaReader;
import io.pravega.connectors.flink.PravegaConfig;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A simple example app that uses a Pravega Writer to write to a given scope and stream.
 */
public class ImageReader {
    // Logger initialization
    private static final Logger LOG = LoggerFactory.getLogger(ImageReader.class);

    public final String scope;
    public final String streamName;
    public final URI controllerURI;

    public ImageReader(String scope, String streamName, URI controllerURI) {
        this.scope = scope;
        this.streamName = streamName;
        this.controllerURI = controllerURI;
    }

    public void run() {
        try {

            //String scope = "image-scope";
            String streamName = "image-stream";
            // Create client config
            PravegaConfig pravegaConfig =  PravegaConfig.fromDefaults()
                    .withControllerURI(controllerURI)
                    .withDefaultScope(scope)
                    .withHostnameValidation(false);;

            LOG.info("==============  pravegaConfig  =============== " + pravegaConfig);

            // create the Pravega input stream (if necessary)
            Stream stream = Utils.createStream(
                    pravegaConfig,
                    streamName);
            LOG.info("==============  stream  =============== " + stream);
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            // create the Pravega source to read a stream of text
            FlinkPravegaReader<ImageData> flinkPravegaReader = FlinkPravegaReader.<ImageData>builder()
                    .withPravegaConfig(pravegaConfig)
                    .forStream(stream)
                    .withDeserializationSchema(new JsonDeserializationSchema(ImageData.class))
                    .build();

            DataStream<ImageData> events = env
                    .addSource(flinkPravegaReader)
                    .name("events");

            // create an output sink to print to stdout for verification
            events.printToErr();

            // execute within the Flink environment
            env.execute("IMAGE Reader");

            LOG.info("########## IMAGE READER END #############");
            // Test whether we are able to create  same image or not
            //ImageToByteArray.createImage(result);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        CommonParams.init(args);
        final String scope = CommonParams.getParam(Constants.SCOPE);
        String streamName = CommonParams.getParam(Constants.STREAM_NAME);
        final URI controllerURI = URI.create(CommonParams.getParam(Constants.CONTROLLER_URI));
        LOG.info("#######################     SCOPE   ###################### " + scope);
        LOG.info("#######################     streamName   ###################### " + streamName);
        LOG.info("#######################     controllerURI   ###################### " + controllerURI);
        ImageReader imageReader = new ImageReader(scope, streamName, controllerURI);
        imageReader.run();
    }
}
