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
package com.dellemc.oe.ingest;

import com.dellemc.oe.serialization.JsonNodeSerializer;
import com.dellemc.oe.util.CommonParams;
import com.dellemc.oe.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.DefaultCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * A simple example app that uses a Pravega Writer to write to a given scope and stream.
 */
public class EventWithTimestampWriter {
    // Logger initialization
    private static final Logger LOG = LoggerFactory.getLogger(JSONWriter.class);

    public final String scope;
    public final String streamName;
    public final URI controllerURI;

    public EventWithTimestampWriter(String scope, String streamName, URI controllerURI) {
        this.scope = scope;
        this.streamName = streamName;
        this.controllerURI = controllerURI;
    }

    // Create a JSON data for testing purpose
    public static ObjectNode createJSONData() {
        ObjectNode message = null;
        try {
            String data = "{\"sensorid\":" + Math.random() + ",\"time\":" + System.currentTimeMillis() + ",\"value\":" + Math.random();
            // Deserialize the JSON message.
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(data);
            message = (ObjectNode) jsonNode;
            LOG.info("@@@@@@@@@@@@@ DATA >>>  " + message.toString());
            return message;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        CommonParams.init(args);
        final String scope = CommonParams.getParam(Constants.SCOPE);
        final String streamName = CommonParams.getParam(Constants.STREAM_NAME);
        final String routingKey = CommonParams.getParam(Constants.ROUTING_KEY_ATTRIBUTE_NAME);
        final URI controllerURI = URI.create(CommonParams.getParam(Constants.CONTROLLER_URI));
        final String dataFile = CommonParams.getParam(Constants.DATA_FILE);
        JSONWriter ew = new JSONWriter(scope, streamName, controllerURI,dataFile);

        ew.run(routingKey);
    }

    public void run(String routingKey) {

        try {
            String streamName = "json-stream";
            // Create client config
            ClientConfig clientConfig = ClientConfig.builder()
                    .controllerURI(controllerURI).build();
            StreamManager streamManager = StreamManager.create(clientConfig);
            StreamConfiguration streamConfig = StreamConfiguration.builder().build();
            if (CommonParams.isPravegaStandalone()) {
                streamManager.createScope(scope);
            }
            streamManager.createStream(scope, streamName, streamConfig);
            // Create EventStreamClientFactory
            EventStreamClientFactory clientFactory = EventStreamClientFactory.withScope(scope, clientConfig);

            // Create event writer
            EventStreamWriter<JsonNode> writer = clientFactory.createEventWriter(
                    streamName,
                    new JsonNodeSerializer(),
                    EventWriterConfig.builder().build());
            // same data write every 1 sec
            while (true) {
                ObjectNode data = createJSONData();
                writer.writeEvent(routingKey, data);
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
