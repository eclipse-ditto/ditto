/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.namespaces;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.utils.ddata.DistributedData;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.ORSetKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.SelfUniqueAddress;

/**
 * Distributed data for blocking of messages addressed entities in certain namespaces.
 */
public final class BlockedNamespaces extends DistributedData<ORSet<String>> {

    /**
     * Role of cluster members to which this distributed data is replicated.
     */
    public static final String CLUSTER_ROLE = "blocked-namespaces-aware";

    /**
     * Name of the replicator actor.
     */
    public static final String ACTOR_NAME = "blockedNamespacesReplicator";

    /**
     * Key of the distributed data. Should be unique among ORSets.
     */
    private static final Key<ORSet<String>> KEY = ORSetKey.create("BlockedNamespaces");

    private static final String BLOCKED_NAMESPACES_DISPATCHER = "blocked-namespaces-dispatcher";

    private final SelfUniqueAddress selfUniqueAddress;

    private BlockedNamespaces(final DistributedDataConfig config, final ActorSystem system) {
        super(config, system, system.dispatchers().lookup(BLOCKED_NAMESPACES_DISPATCHER));
        selfUniqueAddress = SelfUniqueAddress.apply(Cluster.get(system).selfUniqueAddress());
    }

    /**
     * Get an instance of this distributed data with the default configuration. The provided Akka system must be a
     * cluster member with the role {@code blocked-namespaces-aware}.
     *
     * @param system the actor system where the replicator actor will be created.
     * @return a new instance of the distributed data.
     */
    public static BlockedNamespaces of(final ActorSystem system) {
        return Provider.INSTANCE.get(system);
    }

    /**
     * Create an instance of this distributed data with special configuration.
     *
     * @param config the overriding configuration.
     * @param system the actor system where the replicator actor will be created.
     * @return a new instance of the distributed data.
     * @throws NullPointerException if {@code configReader} is {@code null}.
     */
    public static BlockedNamespaces create(final DistributedDataConfig config, final ActorSystem system) {
        return new BlockedNamespaces(config, system);
    }

    /**
     * Test whether a namespace is stored in the local replica with the configured READ timeout.
     *
     * @param namespace the namespace.
     * @return whether the local replica is retrieved successfully and contains the namespace.
     */
    public CompletionStage<Boolean> contains(final String namespace) {
        // for blocked namespaces, only 1 shard is used:
        return get(getKey(0), (Replicator.ReadConsistency) Replicator.readLocal())
                .thenApply(maybeORSet -> maybeORSet.orElse(ORSet.empty()).contains(namespace))
                .exceptionally(error -> false);
    }

    /**
     * Write a namespace to ALL replicas with the configured WRITE timeout.
     *
     * @param namespace the namespace.
     * @return future that completes after the update propagates to all replicas, exceptionally if there is any error.
     */
    public CompletionStage<Void> add(final String namespace) {
        // for blocked namespaces, only 1 shard is used:
        return update(getKey(0), writeAll(), orSet -> orSet.add(selfUniqueAddress, namespace));
    }

    /**
     * Remove a namespace from ALL replicas with the configured WRITE timeout.
     *
     * @param namespace the namespace to remove.
     * @return future that completes after the removal propagates to all replicas, exceptionally if there is any error.
     */
    public CompletionStage<Void> remove(final String namespace) {
        // for blocked namespaces, only 1 shard is used:
        return update(getKey(0), writeAll(), orSet -> orSet.remove(selfUniqueAddress, namespace));
    }

    @Override
    protected Key<ORSet<String>> getKey(final int shardNumber) {
        // for blocked namespaces, only 1 shard is used, so use a static key:
        return KEY;
    }

    @Override
    protected ORSet<String> getInitialValue() {
        return ORSet.empty();
    }

    private Replicator.WriteConsistency writeAll() {
        return new Replicator.WriteAll(writeTimeout);
    }

    private static final class Provider
            extends DistributedData.AbstractDDataProvider<ORSet<String>, BlockedNamespaces> {

        private static final Provider INSTANCE = new Provider();

        private Provider() {}

        @Override
        public BlockedNamespaces createExtension(final ExtendedActorSystem system) {
            return new BlockedNamespaces(DistributedData.createConfig(system, ACTOR_NAME, CLUSTER_ROLE), system);
        }
    }
}
