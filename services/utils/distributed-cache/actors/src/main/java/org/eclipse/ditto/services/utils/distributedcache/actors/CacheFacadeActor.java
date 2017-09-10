/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.distributedcache.actors;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.LWWRegister;
import akka.cluster.ddata.LWWRegisterKey;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.ReplicatorSettings;
import akka.cluster.ddata.ReplicatorSettings$;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Actor responsible for accessing mappings from {@code id} to {@link CacheEntry}.
 * <p>
 * Uses {@link akka.cluster.ddata} to replicate those mappings in the cluster via
 * {@link akka.cluster.ddata.ReplicatedData}.
 * </p>
 */
public final class CacheFacadeActor extends AbstractActor {

    private static final String ACTOR_NAME_SUFFIX = "CacheFacade";
    private static final Timeout ASK_TIMEOUT = Timeout.apply(5, TimeUnit.SECONDS);
    private static final String CONFIG_KEY = "akka.cluster.distributed-data-distributed-cache";
    private static final String CONFIG_KEY_NAME = "name";
    private static final String CONFIG_KEY_ROLE = "role";

    private final DiagnosticLoggingAdapter log = Logging.apply(this);
    private final ActorRef cacheReplicator;
    private final Cluster node = Cluster.get(getContext().system());

    private CacheFacadeActor(final CharSequence cacheRole, final Config config) {
        final ActorSystem system = context().system();
        final String replicatorName = getReplicatorNameFor(cacheRole.toString());
        final String replicatorRole = getReplicatorRoleFor(cacheRole.toString());
        final Config replicatorConfig = config.getConfig(CONFIG_KEY)
                .withValue(CONFIG_KEY_NAME, ConfigValueFactory.fromAnyRef(replicatorName))
                .withValue(CONFIG_KEY_ROLE, ConfigValueFactory.fromAnyRef(replicatorRole));
        final ReplicatorSettings replicatorSettings = ReplicatorSettings$.MODULE$.apply(replicatorConfig);
        cacheReplicator = system.actorOf(Replicator.props(replicatorSettings), replicatorName);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param replicatorName the name for the internal replicator actor.
     * @param config the config for the internal replicator actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final CharSequence replicatorName, final Config config) {
        return Props.create(CacheFacadeActor.class, new Creator<CacheFacadeActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CacheFacadeActor create() throws Exception {
                return new CacheFacadeActor(replicatorName, config);
            }
        });
    }

    /**
     * Returns a name for this Actor suffixed with {@link CacheFacadeActor#ACTOR_NAME_SUFFIX}.
     *
     * @param namePrefix the name prefix.
     * @return the actor name.
     */
    public static String actorNameFor(final CharSequence namePrefix) {
        return namePrefix + ACTOR_NAME_SUFFIX;
    }

    @Override
    public Receive createReceive() {
        //noinspection Convert2MethodRef,unchecked,RedundantCast
        return ReceiveBuilder.create()
                .match(RetrieveCacheEntry.class, this::handleRetrieveCacheEntry)
                .match(ModifyCacheEntry.class, this::handleModifyCacheEntry)
                .match(DeleteCacheEntry.class, this::handleDeleteCacheEntry)
                .match(RegisterForCacheUpdates.class, this::handleRegisterForCacheUpdates)

                // doesn't compile with method references
                .match(Replicator.GetSuccess.class, getSuccess -> handleReplicatorGetSuccess(
                        (Replicator.GetSuccess<LWWRegister<CacheEntry>>) getSuccess))
                .match(Replicator.GetFailure.class,
                        getFailure -> handleReplicatorGetFailure((Replicator.GetFailure) getFailure))
                .match(Replicator.NotFound.class, notFound -> handleReplicatorNotFound((Replicator.NotFound) notFound))
                .match(Replicator.DataDeleted.class,
                        dataDeleted -> handleReplicatorDataDeleted((Replicator.DataDeleted) dataDeleted))
                .matchAny(any -> {
                    log.warning("Got unknown message: {}", any);
                    unhandled(any);
                })
                .build();
    }

    private void handleRetrieveCacheEntry(final RetrieveCacheEntry command) {
        final String id = command.getId();
        final InternalContext context = new InternalContext(id, getSender(), command.getContext().orElse(null),
                System.nanoTime());

        final ReadConsistency readConsistency = command.getReadConsistency();
        cacheReplicator.tell(new Replicator.Get<>(getDataKey(id), readConsistency.getReplicatorConsistency(),
                Optional.of(context)), getSelf());
    }

    private static Key<LWWRegister<CacheEntry>> getDataKey(final String id) {
        return LWWRegisterKey.create(id);
    }

    private void handleModifyCacheEntry(final ModifyCacheEntry command) {
        final ActorRef sender = getSender();
        final String id = command.getId();
        final WriteConsistency writeConsistency = command.getWriteConsistency();
        final CacheEntry cacheEntry = command.getCacheEntry();

        log.debug("Modifying cache entry for id <{}> with write consistency <{}> to value: {}", id,
                writeConsistency, cacheEntry);

        final Replicator.Command<?> replicatorCommand = getReplicatorUpdateCommand(id, cacheEntry, writeConsistency);

        sendCommandAndHandleResponse(sender, replicatorCommand, throwable -> {
            if (null != throwable) {
                log.warning("Modifying cache entry for id <{}> failed: {}", id, throwable.getMessage());
                return ModifyCacheEntryResponse.forFailed(id);
            }
            return ModifyCacheEntryResponse.forSucceeded(id);
        });
    }

    private void handleDeleteCacheEntry(final DeleteCacheEntry command) {
        final ActorRef sender = getSender();
        final String id = command.getId();
        final WriteConsistency writeConsistency = command.getWriteConsistency();
        log.debug("Deleting cache entry for id <{}> with write consistency <{}>.", id, writeConsistency);

        final Replicator.Command<?> replicatorCommand =
                getReplicatorUpdateCommand(id, command.getDeletedCacheEntry(), writeConsistency);

        sendCommandAndHandleResponse(sender, replicatorCommand, t -> {
            if (null != t) {
                log.warning("Deleting cache entry for id <{}> failed: {}", id, t.getMessage());
                return DeleteCacheEntryResponse.forFailed(id);
            }
            return DeleteCacheEntryResponse.forSucceeded(id);
        });
    }

    private Replicator.Command<LWWRegister<CacheEntry>> getReplicatorUpdateCommand(final String id,
            final CacheEntry cacheEntry, final Consistency<Replicator.WriteConsistency> writeConsistency) {

        return getReplicatorUpdateCommand(id, writeConsistency, cacheEntry.getRevision(), cacheEntry,
                ignored -> cacheEntry);
    }

    private Replicator.Command<LWWRegister<CacheEntry>> getReplicatorUpdateCommand(final String id,
            final Consistency<Replicator.WriteConsistency> writeConsistency,
            final long revision,
            final CacheEntry initialCacheEntry,
            final Function<CacheEntry, CacheEntry> modifyCacheEntry) {

        final LWWRegister.Clock<CacheEntry> revisionClock = (currentTimestamp, value) -> revision;

        return new Replicator.Update<>(
                getDataKey(id),
                LWWRegister.create(node, initialCacheEntry, revisionClock),
                writeConsistency.getReplicatorConsistency(),
                Optional.ofNullable(getSelf()),
                r -> r.withValue(node, modifyCacheEntry.apply(r.getValue()), revisionClock)
        );
    }

    private void sendCommandAndHandleResponse(final ActorRef sender, final Replicator.Command<?> command,
            final Function<Throwable, CacheCommandResponse> createResponseFunction) {

        PatternsCS.ask(cacheReplicator, command, ASK_TIMEOUT)
                .whenComplete((response, throwable) -> {
                    final CacheCommandResponse cacheCommandResponse = createResponseFunction.apply(throwable);
                    sender.tell(cacheCommandResponse, ActorRef.noSender());
                });
    }

    private void handleRegisterForCacheUpdates(final RegisterForCacheUpdates command) {
        cacheReplicator.tell(new Replicator.Subscribe<>(getDataKey(command.getId()), command.getSubscriber()),
                getSelf());
    }

    private void handleReplicatorGetSuccess(final Replicator.GetSuccess<LWWRegister<CacheEntry>> getSuccess) {
        final LWWRegister<CacheEntry> register = getSuccess.dataValue();
        respondToCommand(getSuccess.getRequest(), getSuccess.getClass(), register.getValue());
    }

    private void handleReplicatorGetFailure(final Replicator.GetFailure getFailure) {
        log.debug("Failure response for key: {}", getFailure.key());
        respondToCommand(getFailure.getRequest(), getFailure.getClass(), null);
    }

    private void handleReplicatorNotFound(final Replicator.NotFound notFound) {
        log.debug("Key not found: {}", notFound.key());
        respondToCommand(notFound.getRequest(), notFound.getClass(), null);
    }

    private void handleReplicatorDataDeleted(final Replicator.DataDeleted dataDeleted) {
        log.debug("Key was deleted: {}", dataDeleted.key());
        respondToCommand(dataDeleted.getRequest(), dataDeleted.getClass(), null);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void respondToCommand(final Optional<?> request, final Class<?> commandClass,
            @Nullable final CacheEntry cacheEntry) {

        request.filter(ctx -> ctx instanceof InternalContext)
                .map(ctx -> (InternalContext) ctx)
                .ifPresent(ctx -> {
                    final String id = ctx.getId();
                    log.debug("Duration of <{}> command for id <{}> was <{}> ms.", commandClass.getSimpleName(), id,
                            getDurationMs(ctx));
                    final ActorRef sender = ctx.getSender();
                    sender.tell(new RetrieveCacheEntryResponse(id, cacheEntry, ctx.getContext().orElse(null)),
                            getSelf());
                });
    }

    private static double getDurationMs(final InternalContext context) {
        return (System.nanoTime() - context.getStartTs()) / 1_000_000.0;
    }

    private static String getReplicatorNameFor(final String cacheRole) {
        return "ddata" + Character.toUpperCase(cacheRole.charAt(0)) + cacheRole.substring(1) + "CacheReplicator";
    }

    private static String getReplicatorRoleFor(final String cacheRole) {
        return cacheRole + "-cache-aware";
    }

    /**
     * Internal context for retrieving the complete cache or just a {@link CacheEntry} identified by an {@code id}
     * containing also the ActorRef of the original sender.
     */
    @NotThreadSafe
    private static final class InternalContext {

        private final String id;
        private final ActorRef sender;
        @Nullable private final Object context;
        private final long startTs;

        private InternalContext(final String id, final ActorRef sender, @Nullable final Object context,
                final long startTs) {

            this.id = id;
            this.sender = sender;
            this.context = context;
            this.startTs = startTs;
        }

        private String getId() {
            return id;
        }

        private ActorRef getSender() {
            return sender;
        }

        private Optional<Object> getContext() {
            return Optional.ofNullable(context);
        }

        private long getStartTs() {
            return startTs;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final InternalContext that = (InternalContext) o;
            return startTs == that.startTs &&
                    Objects.equals(id, that.id) &&
                    Objects.equals(sender, that.sender) &&
                    Objects.equals(context, that.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, sender, context, startTs);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "id=" + id +
                    ", sender=" + sender +
                    ", context=" + context +
                    ", startTs=" + startTs +
                    "]";
        }

    }

}
