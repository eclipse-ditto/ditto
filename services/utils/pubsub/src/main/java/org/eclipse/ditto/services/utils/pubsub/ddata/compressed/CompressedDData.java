/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;

import akka.actor.ActorSystem;
import akka.util.ByteString;

/**
 * Access to distributed data of compressed topics.
 */
public final class CompressedDData implements DData<ByteString, CompressedUpdate> {

    private CompressedDDataHandler handler;

    private CompressedDData(final CompressedDDataHandler handler) {
        this.handler = handler;
    }

    /**
     * Start distributed-data replicator for compressed topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param ddataConfig the distributed data config.
     * @param topicType the type of messages, typically the canonical name of the message class.
     * @param pubSubConfig the pub-sub config.
     * @return access to the distributed data.
     */
    public static CompressedDData of(final ActorSystem system, final DistributedDataConfigReader ddataConfig,
            final String topicType, final PubSubConfig pubSubConfig) {

        return new CompressedDData(CompressedDDataHandler.of(system, ddataConfig, topicType, pubSubConfig));
    }

    @Override
    public DDataReader<ByteString> getReader() {
        return handler;
    }

    @Override
    public DDataWriter<CompressedUpdate> getWriter() {
        return handler;
    }

    @Override
    public Subscriptions<CompressedUpdate> createSubscriptions() {
        return CompressedSubscriptions.of(handler.getSeeds());
    }
}
