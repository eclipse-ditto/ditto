/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.namespaces;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.services.utils.ddata.DistributedData;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.ORSetKey;
import akka.cluster.ddata.Replicator;
import scala.concurrent.duration.FiniteDuration;

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

    private final Cluster node;

    private BlockedNamespaces(final DistributedDataConfigReader configReader, final ActorSystem system) {
        super(configReader, system, system.dispatchers().lookup(BLOCKED_NAMESPACES_DISPATCHER));
        node = Cluster.get(system);
    }

    /**
     * Create an instance of this distributed data with the default configuration. The provided Akka system must be a
     * cluster member with the role {@code blocked-namespaces-aware}.
     *
     * @param system the actor system where the replicator actor will be created.
     * @return a new instance of the distributed data.
     */
    public static BlockedNamespaces of(final ActorSystem system) {
        return new BlockedNamespaces(DistributedDataConfigReader.of(system, ACTOR_NAME, CLUSTER_ROLE), system);
    }

    /**
     * Create an instance of this distributed data with special configuration.
     *
     * @param configReader the overriding configuration.
     * @param system the actor system where the replicator actor will be created.
     * @return a new instance of the distributed data.
     * @throws NullPointerException if {@code configReader} is {@code null}.
     */
    public static BlockedNamespaces of(final DistributedDataConfigReader configReader, final ActorSystem system) {
        return new BlockedNamespaces(configReader, system);
    }

    /**
     * Test whether a namespace is stored in the local replica with the configured READ timeout.
     *
     * @param namespace the namespace.
     * @return whether the local replica is retrieved successfully and contains the namespace.
     */
    public CompletionStage<Boolean> contains(final String namespace) {
        return get(Replicator.readLocal())
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
        return update(writeAll(), orSet -> orSet.add(node, namespace));
    }

    /**
     * Remove a namespace from ALL replicas with the configured WRITE timeout.
     *
     * @param namespace the namespace to remove.
     * @return future that completes after the removal propagates to all replicas, exceptionally if there is any error.
     */
    public CompletionStage<Void> remove(final String namespace) {
        return update(writeAll(), orSet -> orSet.remove(node, namespace));
    }

    @Override
    protected Key<ORSet<String>> getKey() {
        return KEY;
    }

    @Override
    protected ORSet<String> getInitialValue() {
        return ORSet.empty();
    }

    private Replicator.WriteConsistency writeAll() {
        return new Replicator.WriteAll(FiniteDuration.apply(writeTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }

}
