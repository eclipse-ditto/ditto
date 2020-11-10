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
package org.eclipse.ditto.services.utils.pubsub.ddata.ack;

import java.util.Set;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.ddata.DData;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataReader;
import org.eclipse.ditto.services.utils.pubsub.ddata.DDataWriter;
import org.eclipse.ditto.services.utils.pubsub.ddata.Subscriptions;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.ExtendedActorSystem;
import akka.cluster.ddata.ORMultiMap;
import akka.japi.Pair;

/**
 * Access to distributed data of declared acknowledgement labels.
 */
public final class AckDData implements DData<Address, Pair<String, Set<String>>, AckDDataUpdate> {

    private final AckDDataHandler handler;

    private AckDData(final AckDDataHandler handler) {
        this.handler = handler;
    }

    /**
     * Start distributed-data replicator under an actor system's user guardian using the default dispatcher.
     *
     * @param system the actor system.
     * @param provider the ddata extension provider.
     * @return access to the distributed data.
     */
    public static AckDData of(final ActorSystem system, final Provider provider) {
        return new AckDData(provider.get(system));
    }

    /**
     * Package an existing distributed data extension.
     *
     * @param extension the ddata extension.
     * @return access to the distributed data.
     */
    public static AckDData of(final AckDDataHandler extension) {
        return new AckDData(extension);
    }

    @Override
    public DDataReader<Address, Pair<String, Set<String>>> getReader() {
        return handler;
    }

    @Override
    public DDataWriter<Address, AckDDataUpdate> getWriter() {
        return handler;
    }

    @Override
    public Subscriptions<AckDDataUpdate> createSubscriptions() {
        throw new UnsupportedOperationException("TODO: delete this method");
    }

    // TODO: javadoc
    public static final class Provider extends
            DistributedData.AbstractDDataProvider<ORMultiMap<Address, Pair<String, Set<String>>>, AckDDataHandler> {

        private final String clusterRole;
        private final String messageType;

        private Provider(final String clusterRole, final String messageType) {
            this.clusterRole = clusterRole;
            this.messageType = messageType;
        }

        /**
         * Create a distributed data provider.
         *
         * @param clusterRole Cluster role where this provider start.
         * @param messageType Message type that uniquely identifies this provider.
         * @return the ddata provider.
         */
        public static Provider of(final String clusterRole, final String messageType) {
            return new Provider(clusterRole, messageType);
        }

        @Override
        public AckDDataHandler createExtension(final ExtendedActorSystem system) {
            return AckDDataHandler.create(system, getConfig(system), messageType);
        }

        // TODO: javadoc
        public DistributedDataConfig getConfig(final ActorSystem actorSystem) {
            return DistributedData.createConfig(actorSystem, messageType + "-replicator", clusterRole);
        }
    }
}
