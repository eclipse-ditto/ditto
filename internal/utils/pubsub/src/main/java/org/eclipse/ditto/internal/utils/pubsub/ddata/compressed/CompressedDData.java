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
package org.eclipse.ditto.internal.utils.pubsub.ddata.compressed;

import java.util.List;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DData;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.internal.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralUpdate;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.ddata.ORMultiMap;

/**
 * Access to distributed data of compressed topics.
 */
public final class CompressedDData implements DData<ActorRef, String, LiteralUpdate> {

    private final CompressedDDataHandler handler;

    private CompressedDData(final CompressedDDataHandler handler) {
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
    public static CompressedDData of(final ActorSystem system, final Provider provider) {
        return new CompressedDData(provider.get(system));
    }

    /**
     * Package an existing distributed data extension.
     *
     * @param extension the ddata extension.
     * @return access to the distributed data.
     */
    public static CompressedDData of(final CompressedDDataHandler extension) {
        return new CompressedDData(extension);
    }

    @Override
    public DDataReader<ActorRef, String> getReader() {
        return handler;
    }

    @Override
    public DDataWriter<ActorRef, LiteralUpdate> getWriter() {
        return handler;
    }

    @Override
    public DistributedDataConfig getConfig() {
        return handler.getConfig();
    }

    /**
     * Get the hash seeds of the compressed DData.
     *
     * @return the hash seeds.
     */
    public List<Integer> getSeeds() {
        return handler.getSeeds();
    }

    /**
     * Abstract class of distributed data extension provider to be instantiated at user site.
     */
    public abstract static class Provider
            extends DistributedData.AbstractDDataProvider<ORMultiMap<ActorRef, String>, CompressedDDataHandler> {

        /**
         * Get the ddata extension's config from an actor system.
         *
         * @param actorSystem The actor system.
         * @return The ddata extension's config.
         */
        public abstract DistributedDataConfig getConfig(ActorSystem actorSystem);
    }
}
