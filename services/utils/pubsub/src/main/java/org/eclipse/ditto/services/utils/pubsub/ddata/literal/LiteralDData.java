/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.utils.pubsub.ddata.literal;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.ddata.ORMultiMap;

/**
 * Access to distributed data of literal topics.
 */
public final class LiteralDData implements DData<String, LiteralUpdate> {

    private final LiteralDDataHandler handler;

    private LiteralDData(final LiteralDDataHandler handler) {
        this.handler = handler;
    }

    /**
     * Start distributed-data replicator for compressed topics under an actor system's user guardian using the default
     * dispatcher.
     *
     * @param system the actor system.
     * @param provider the ddata extension provider.
     * @return access to the distributed data.
     */
    public static LiteralDData of(final ActorSystem system, final Provider provider) {
        return new LiteralDData(provider.get(system));
    }

    /**
     * Package an existing distributed data extension.
     *
     * @param extension the ddata extension.
     * @return access to the distributed data.
     */
    public static LiteralDData of(final LiteralDDataHandler extension) {
        return new LiteralDData(extension);
    }

    @Override
    public DDataReader<String> getReader() {
        return handler;
    }

    @Override
    public DDataWriter<LiteralUpdate> getWriter() {
        return handler;
    }

    @Override
    public Subscriptions<LiteralUpdate> createSubscriptions() {
        return LiteralSubscriptions.newInstance();
    }

    /**
     * Abstract class of distributed data extension provider to be instantiated at user site.
     */
    public abstract static class Provider
            extends DistributedData.AbstractDDataProvider<ORMultiMap<ActorRef, String>, LiteralDDataHandler> {

        /**
         * Get the ddata extension's config from an actor system.
         *
         * @param actorSystem The actor system.
         * @return The ddata extension's config.
         */
        public abstract DistributedDataConfig getConfig(ActorSystem actorSystem);
    }
}
