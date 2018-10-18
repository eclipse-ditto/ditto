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

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import scala.concurrent.duration.FiniteDuration;

/**
 * Supertype of typed interfaces for distributed data. Each instance corresponds to one distributed data object.
 * Each instance starts its own replicator so that it can have its own configuration regarding e. g. roles of cluster
 * members to which the data gets replicated.
 *
 * @param <R> type of replicated data.
 */
public abstract class DistributedData<R extends ReplicatedData> {

    /**
     * Default timeout of read operations.
     */
    protected final Duration readTimeout;

    /**
     * Default timeout of write operations.
     */
    protected final Duration writeTimeout;

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
    protected DistributedData(final DDataConfigReader configReader, final ActorRefFactory factory) {
        replicator = createReplicator(configReader, factory);
        readTimeout = configReader.readTimeout();
        writeTimeout = configReader.writeTimeout();
    }

    /**
     * @return key of the distributed collection. Should be unique among collections of the same type.
     */
    protected abstract Key<R> key();

    /**
     * @return initial value of the distributed data.
     */
    protected abstract R initialValue();

    /**
     * Retrieve the replicated data.
     *
     * @param readConsistency how many replicas to consult.
     * @return future of the replicated data that completes exceptionally on error.
     */
    public CompletionStage<Optional<R>> get(final Replicator.ReadConsistency readConsistency) {
        final Replicator.Get<R> replicatorGet = new Replicator.Get<>(key(), readConsistency);
        return PatternsCS.ask(replicator, replicatorGet, getAskTimeout(readConsistency.timeout(), readTimeout))
                .thenApply(this::handleGetResponse);
    }

    /**
     * Modify the replicated data.
     *
     * @param writeConsistency how many replicas to update.
     * @param updateFunction what to do to the replicas.
     * @return future that completes when the update completes, exceptionally when any error is encountered.
     */
    public CompletionStage<Void> update(final Replicator.WriteConsistency writeConsistency,
            final Function<R, R> updateFunction) {

        final Replicator.Update<R> replicatorUpdate =
                new Replicator.Update<>(key(), initialValue(), writeConsistency, updateFunction);
        return PatternsCS.ask(replicator, replicatorUpdate, getAskTimeout(writeConsistency.timeout(), writeTimeout))
                .thenApply(this::handleUpdateResponse);
    }

    /**
     * @return reference to the distributed data replicator.
     */
    public ActorRef getReplicator() {
        return replicator;
    }

    private Void handleUpdateResponse(final Object reply) {
        if (reply instanceof Replicator.UpdateSuccess) {
            return null;
        } else {
            final String errorMessage =
                    MessageFormat.format("Expect Replicator.UpdateSuccess for key ''{2}'' from ''{1}'', Got: ''{0}''",
                            reply, replicator, key());
            throw new IllegalArgumentException(errorMessage);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<R> handleGetResponse(final Object reply) {
        if (reply instanceof Replicator.GetSuccess) {
            final Replicator.GetSuccess<R> getSuccess = (Replicator.GetSuccess<R>) reply;
            return Optional.of(getSuccess.dataValue());
        } else if (reply instanceof Replicator.NotFound) {
            return Optional.empty();
        } else {
            final String errorMessage =
                    MessageFormat.format("Expect Replicator.GetResponse for key ''{2}'' from ''{1}'', Got: ''{0}''",
                            reply, replicator, key());
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Compute timeout from a given read/write consistency and a default. If the timeout from the read/write
     * consistency is positive, then it is taken. Otherwise the default timeout is taken.
     *
     * @param givenTimeout timeout from a read/write consistency.
     * @param defaultTimeout default timeout.
     * @return the timeout.
     */
    private static Timeout getAskTimeout(final FiniteDuration givenTimeout, final Duration defaultTimeout) {
        if (givenTimeout.gt(FiniteDuration.Zero())) {
            return Timeout.durationToTimeout(givenTimeout);
        } else {
            return Timeout.create(defaultTimeout);
        }
    }

    /**
     * Create a distributed data replicator in an actor system.
     *
     * @param configReader distributed data configuration reader.
     * @param factory creator of this replicator.
     * @return reference to the created replicator.
     */
    private static ActorRef createReplicator(final DDataConfigReader configReader, final ActorRefFactory factory) {
        return factory.actorOf(Replicator.props(configReader.toReplicatorSettings()), configReader.name());
    }
}
