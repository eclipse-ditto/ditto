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
package org.eclipse.ditto.internal.utils.ddata;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ReplicatedData;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.ReplicatorSettings;
import akka.pattern.Patterns;
import scala.concurrent.duration.FiniteDuration;

/**
 * Supertype of typed interfaces for distributed data. Each instance corresponds to one distributed data object.
 * Each instance starts its own replicator so that it can have its own configuration regarding e. g. roles of cluster
 * members to which the data gets replicated.
 *
 * @param <R> type of replicated data.
 */
public abstract class DistributedData<R extends ReplicatedData> implements Extension {

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

    protected final int numberOfShards;

    private final Executor ddataExecutor;

    private final DistributedDataConfig config;

    /**
     * Create a wrapper of distributed data replicator.
     *
     * @param config specific config for this replicator.
     * @param factory creator of this replicator.
     * @throws NullPointerException if {@code configReader} is {@code null}.
     */
    protected DistributedData(final DistributedDataConfig config, final ActorRefFactory factory,
            final Executor ddataExecutor) {
        requireNonNull(config, "The DistributedDataConfig must not be null!");
        replicator = createReplicator(config, factory);
        this.ddataExecutor = ddataExecutor;
        readTimeout = config.getReadTimeout();
        writeTimeout = config.getWriteTimeout();
        numberOfShards = config.getNumberOfShards();
        this.config = config;
    }

    /**
     * Create a distributed data replicator in an actor system.
     *
     * @param config distributed data configuration reader.
     * @param factory creator of this replicator.
     * @return reference to the created replicator.
     */
    private static ActorRef createReplicator(final DistributedDataConfig config, final ActorRefFactory factory) {

        final AkkaReplicatorConfig akkaReplicatorConfig = config.getAkkaReplicatorConfig();
        return factory.actorOf(Replicator.props(ReplicatorSettings.apply(akkaReplicatorConfig.getCompleteConfig())),
                akkaReplicatorConfig.getName());
    }

    /**
     * Create a distributed data config with Akka's default options.
     *
     * @param replicatorName the name of the replicator.
     * @param replicatorRole the cluster role of members with replicas of the distributed collection.
     * @return a new config object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DistributedDataConfig createConfig(final ActorSystem actorSystem,
            final CharSequence replicatorName, final CharSequence replicatorRole) {

        return DefaultDistributedDataConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()),
                replicatorName, replicatorRole);
    }

    /**
     * Creates/gets a key for the passed {@code shardNumber}.
     *
     * @param shardNumber the number of the shard to append to the key.
     * @return key of the distributed collection. Should be unique among collections of the same type.
     */
    protected abstract Key<R> getKey(int shardNumber);

    /**
     * @return initial value of the distributed data.
     */
    protected abstract R getInitialValue();

    /**
     * Asynchronously retrieves the replicated data.
     *
     * @param key the key to get the replicated data for.
     * @param readConsistency how many replicas to consult.
     * @return future of the replicated data that completes exceptionally on error.
     */
    public CompletionStage<Optional<R>> get(final Key<R> key, final Replicator.ReadConsistency readConsistency) {
        final Replicator.Get<R> replicatorGet = new Replicator.Get<>(key, readConsistency);
        return Patterns.ask(replicator, replicatorGet, getAskTimeout(readConsistency.timeout(), readTimeout))
                .thenApplyAsync(reply -> this.handleGetResponse(reply, key), ddataExecutor);
    }

    /**
     * Modify the replicated data.
     *
     * @param key the key to update.
     * @param writeConsistency how many replicas to update.
     * @param updateFunction what to do to the replicas.
     * @return future that completes when the update completes, exceptionally when any error is encountered.
     */
    public CompletionStage<Void> update(final Key<R> key, final Replicator.WriteConsistency writeConsistency,
            final Function<R, R> updateFunction) {

        final Replicator.Update<R> replicatorUpdate =
                new Replicator.Update<>(key, getInitialValue(), writeConsistency, updateFunction);
        return Patterns.ask(replicator, replicatorUpdate, getAskTimeout(writeConsistency.timeout(), writeTimeout))
                .thenApplyAsync(reply -> this.handleUpdateResponse(reply, key), ddataExecutor);
    }

    /**
     * Request updates when the distributed data changes.
     *
     * @param subscriber whom to notify of changes.
     */
    public void subscribeForChanges(final ActorRef subscriber) {
        IntStream.range(0, numberOfShards)
                .forEach(i -> replicator.tell(new Replicator.Subscribe<>(getKey(i), subscriber), ActorRef.noSender()));
    }

    /**
     * @return reference to the distributed data replicator.
     */
    public ActorRef getReplicator() {
        return replicator;
    }

    /**
     * Get the config of this distributed data.
     *
     * @return The config.
     * @since 3.0.0
     */
    public DistributedDataConfig getConfig() {
        return config;
    }

    private Void handleUpdateResponse(final Object reply, final Key<R> key) {
        if (reply instanceof Replicator.UpdateSuccess) {
            return null;
        } else {
            final String errorMessage =
                    MessageFormat.format("Expect Replicator.UpdateSuccess for key ''{2}'' from ''{1}'', Got: ''{0}''",
                            reply, replicator, key);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<R> handleGetResponse(final Object reply, final Key<R> key) {
        if (reply instanceof Replicator.GetSuccess) {
            final Replicator.GetSuccess<R> getSuccess = (Replicator.GetSuccess<R>) reply;
            return Optional.of(getSuccess.dataValue());
        } else if (reply instanceof Replicator.NotFound) {
            return Optional.empty();
        } else {
            final String errorMessage =
                    MessageFormat.format("Expect Replicator.GetResponse for key ''{2}'' from ''{1}'', Got: ''{0}''",
                            reply, replicator, key);
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

    /**
     * Extension provider for Ditto distributed data.
     *
     * @param <R> type of distributed data.
     * @param <T> type of the actor system extension to handle the distributed data.
     */
    public abstract static class AbstractDDataProvider<R extends ReplicatedData, T extends DistributedData<R>>
            extends AbstractExtensionId<T> {

        /**
         * Constructor available for subclasses only.
         */
        protected AbstractDDataProvider() {
        }

        @Override
        public abstract T createExtension(ExtendedActorSystem system);

        /**
         * Lookup an extension provider to load an extension from config on actor system startup.
         * Required by ExtensionIdProvider; not used by Ditto.
         *
         * @return this object.
         */
        public AbstractDDataProvider<R, T> lookup() {
            return this;
        }
    }

}
