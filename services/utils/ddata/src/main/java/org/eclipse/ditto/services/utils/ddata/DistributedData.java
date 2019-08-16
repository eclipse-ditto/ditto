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
package org.eclipse.ditto.services.utils.ddata;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.pattern.Patterns;
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

    private final Executor ddataExecutor;

    /**
     * Create a wrapper of distributed data replicator.
     *
     * @param configReader specific config for this replicator.
     * @param factory creator of this replicator.
     * @throws NullPointerException if {@code configReader} is {@code null}.
     */
    protected DistributedData(final DistributedDataConfigReader configReader, final ActorRefFactory factory,
            final Executor ddataExecutor) {
        requireNonNull(configReader, "The DistributedDataConfigReader must not be null!");
        replicator = createReplicator(configReader, factory);
        this.ddataExecutor = ddataExecutor;
        readTimeout = configReader.getReadTimeout();
        writeTimeout = configReader.getWriteTimeout();
    }

    /**
     * Create a distributed data replicator in an actor system.
     *
     * @param configReader distributed data configuration reader.
     * @param factory creator of this replicator.
     * @return reference to the created replicator.
     */
    private static ActorRef createReplicator(final DistributedDataConfigReader configReader,
            final ActorRefFactory factory) {

        return factory.actorOf(Replicator.props(configReader.toReplicatorSettings()), configReader.getName());
    }

    /**
     * @return key of the distributed collection. Should be unique among collections of the same type.
     */
    protected abstract Key<R> getKey();

    /**
     * @return initial value of the distributed data.
     */
    protected abstract R getInitialValue();

    /**
     * Asynchronously retrieves the replicated data.
     *
     * @param readConsistency how many replicas to consult.
     * @return future of the replicated data that completes exceptionally on error.
     */
    public CompletionStage<Optional<R>> get(final Replicator.ReadConsistency readConsistency) {
        final Replicator.Get<R> replicatorGet = new Replicator.Get<>(getKey(), readConsistency);
        return Patterns.ask(replicator, replicatorGet, getAskTimeout(readConsistency.timeout(), readTimeout))
                .thenApplyAsync(this::handleGetResponse, ddataExecutor);
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
                new Replicator.Update<>(getKey(), getInitialValue(), writeConsistency, updateFunction);
        return Patterns.ask(replicator, replicatorUpdate, getAskTimeout(writeConsistency.timeout(), writeTimeout))
                .thenApplyAsync(this::handleUpdateResponse, ddataExecutor);
    }

    /**
     * Request updates when the distributed data changes.
     *
     * @param subscriber whom to notify of changes.
     */
    public void subscribeForChanges(final ActorRef subscriber) {
        replicator.tell(new Replicator.Subscribe<>(getKey(), subscriber), ActorRef.noSender());
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
                            reply, replicator, getKey());
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
                            reply, replicator, getKey());
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Compute timeout from a given read/write consistency and a default. If the timeout from the read/write
     * consistency is positive, then it is taken. Otherwise the default timeout is taken.
     *
     * @param defaultTimeout timeout from a read/write consistency.
     * @param configuredTimeout default timeout.
     * @return the timeout.
     */
    private static Duration getAskTimeout(final FiniteDuration defaultTimeout, final Duration configuredTimeout) {
        if (configuredTimeout.isNegative()) {
            return Duration.ofMillis(defaultTimeout.toMillis());
        } else {
            return configuredTimeout;
        }
    }

}
