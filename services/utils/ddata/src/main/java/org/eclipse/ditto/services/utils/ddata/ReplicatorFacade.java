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
package org.eclipse.ditto.services.utils.ddata;

import java.time.Duration;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.cluster.ddata.Replicator;

/**
 * Supertype of typed interfaces for distributed data replicators that support multiple distributed collections per
 * cluster member each with a different configuration. Use cases include replicating each distributed collection to
 * different cluster members according to their roles. Distributed data replicators are created in user space.
 *
 * @param <R> type of replicated data.
 */
public class ReplicatorFacade<R> {

    private final Duration askTimeout;

    /**
     * Reference of the distributed data replicator.
     */
    protected final ActorRef replicator;

    /**
     * Create a wrapper of distributed data replicator.
     *
     * @param configReader specific config for this replicator.
     * @param factory creator of this replicator.
     */
    protected ReplicatorFacade(final DDataConfigReader configReader, final ActorRefFactory factory) {
        replicator = createReplicator(configReader, factory);
        askTimeout = configReader.askTimeout();
    }

    /**
     * Create a distributed data replicator in an actor system.
     *
     * @param configReader distributed data configuration reader.
     * @param factory creator of this replicator.
     * @return reference to the created replicator.
     */
    public static ActorRef createReplicator(final DDataConfigReader configReader, final ActorRefFactory factory) {
        return factory.actorOf(Replicator.props(configReader.toReplicatorSettings()), configReader.name());
    }
}
