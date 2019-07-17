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

import java.net.URI;

import java.util.concurrent.CompletableFuture;
import io.pravega.client.ClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.stream.impl.JavaSerializer;

import com.dellemc.oe.util.CommonParams;
/**
 * A simple example app that uses a Pravega Writer to write to a given scope and stream.
 */
public class EventWriter {

    public final String scope;
    public final String streamName;
    public final URI controllerURI;

    public EventWriter(String scope, String streamName, URI controllerURI) {
        this.scope = scope;
        this.streamName = streamName;
        this.controllerURI = controllerURI;
    }

    public void run(String routingKey, String message)  {

        try (ClientFactory clientFactory = ClientFactory.withScope(scope, controllerURI);
             EventStreamWriter<String> writer = clientFactory.createEventWriter(streamName,
                     new JavaSerializer<String>(),
                     EventWriterConfig.builder().build())) {
						 
						 
						 System.out.format("Writing message: '%s' with routing-key: '%s' to stream '%s / %s'%n",
						 message, routingKey, scope, streamName);
						 
						 /*writes the given non-null Event object to the Stream using a given Routing key to determine which Stream Segment it should written to. writeEvent() is asynchronous and return some sort of Future object, which will complete when the event has been durably stored on the configured number of replicas, and is available for readers to see. Failures that occur are handled internally with multiple retires and exponential backoff. So there is no need to attempt to retry in the event of an exception*/
						 final CompletableFuture writeFuture = writer.writeEvent(routingKey, message);
                
            }
    }

    public static void main(String[] args) {
        final String scope = CommonParams.getScope();
        final String streamName = CommonParams.getStreamName();
        final String routingKey = CommonParams.getRoutingKeyAttributeName();
        final String message = CommonParams.getMessage();;
        final URI controllerURI = CommonParams.getControllerURI();
        EventWriter ew = new EventWriter(scope, streamName, controllerURI);
        ew.run(routingKey, message);
    }
}
