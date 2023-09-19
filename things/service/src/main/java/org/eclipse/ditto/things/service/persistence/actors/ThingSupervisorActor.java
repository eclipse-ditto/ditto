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
package org.eclipse.ditto.things.service.persistence.actors;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.base.service.config.supervision.LocalAskTimeoutConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.cluster.StopShardedActor;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.persistentactors.TargetActorWithMessage;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.policies.api.PoliciesMessagingConstants;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.signals.commands.modify.DeletePolicy;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.enforcement.ThingEnforcement;
import org.eclipse.ditto.things.service.enforcement.ThingEnforcerActor;
import org.eclipse.ditto.things.service.enforcement.ThingPolicyCreated;
import org.eclipse.ditto.thingsearch.api.ThingsSearchConstants;

import org.apache.pekko.actor.ActorKilledException;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.FI;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Supervisor for {@link ThingPersistenceActor} which means it will create, start and watch it as child actor.
 * <p>
 * If the child terminates, it will wait for the calculated exponential back-off time and restart it afterwards.
 * Between the termination of the child and the restart, this actor answers to all requests with a
 * {@link ThingUnavailableException} as fail fast strategy.
 * </p>
 */
public final class ThingSupervisorActor extends AbstractPersistenceSupervisor<ThingId, Signal<?>> {

    private final ActorRef pubSubMediator;
    private final ActorRef policiesShardRegion;
    private final ActorRef thingsShardRegion;
    private final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin;
    @Nullable private final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory;
    private final DefaultEnforcementConfig enforcementConfig;
    private final Materializer materializer;
    private final ResponseReceiverCache responseReceiverCache;

    private final SupervisorInlinePolicyEnrichment inlinePolicyEnrichment;
    private final SupervisorLiveChannelDispatching liveChannelDispatching;
    private final SupervisorSmartChannelDispatching smartChannelDispatching;

    private final PolicyEnforcerProvider policyEnforcerProvider;
    private final ActorRef searchShardRegionProxy;

    private final Duration shutdownTimeout;
    @Nullable
    private ThingPolicyCreated policyCreatedEvent;

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            @Nullable final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            @Nullable final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory,
            @Nullable final ActorRef thingPersistenceActorRef,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        super(blockedNamespaces, mongoReadJournal);

        this.policyEnforcerProvider = policyEnforcerProvider;
        this.pubSubMediator = pubSubMediator;
        this.distributedPubThingEventsForTwin = distributedPubThingEventsForTwin;
        this.thingPersistenceActorPropsFactory = thingPersistenceActorPropsFactory;
        persistenceActorChild = thingPersistenceActorRef;
        final var system = getContext().getSystem();
        final var dittoScoped = DefaultScopedConfig.dittoScoped(system.settings().config());
        enforcementConfig = DefaultEnforcementConfig.of(dittoScoped);
        final var thingsConfig = DittoThingsConfig.of(dittoScoped);
        shutdownTimeout = thingsConfig.getThingConfig().getShutdownTimeout();

        materializer = Materializer.createMaterializer(getContext());
        responseReceiverCache = ResponseReceiverCache.lookup(system);

        final ActorSelection thingPersistenceActorSelection;
        if (null != persistenceActorChild) {
            thingPersistenceActorSelection = getContext().actorSelection(persistenceActorChild.path());
        } else {
            // we use a ActorSelection so that we
            // a) do not have to have the persistence actor initialized at this point
            // b) must not react on recreation of the persistence actor by recreating the classes using it
            thingPersistenceActorSelection = getContext().actorSelection(PERSISTENCE_ACTOR_NAME);
        }

        final ShardRegionProxyActorFactory shardRegionProxyActorFactory =
                ShardRegionProxyActorFactory.newInstance(system, thingsConfig.getClusterConfig());

        if (null != policiesShardRegion) {
            this.policiesShardRegion = policiesShardRegion;
        } else {
            this.policiesShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                    PoliciesMessagingConstants.CLUSTER_ROLE,
                    PoliciesMessagingConstants.SHARD_REGION
            );
        }
        thingsShardRegion = shardRegionProxyActorFactory.getShardRegionProxyActor(
                ThingsMessagingConstants.CLUSTER_ROLE,
                ThingsMessagingConstants.SHARD_REGION
        );
        searchShardRegionProxy =
                shardRegionProxyActorFactory.getShardRegionProxyActor(ThingsSearchConstants.CLUSTER_ROLE,
                        ThingsSearchConstants.SHARD_REGION);

        try {
            inlinePolicyEnrichment = new SupervisorInlinePolicyEnrichment(system, log, getEntityId(),
                    thingPersistenceActorSelection, this.policiesShardRegion, enforcementConfig);
        } catch (final Exception e) {
            throw new IllegalStateException("Entity Id could not be retrieved", e);
        }
        liveChannelDispatching = new SupervisorLiveChannelDispatching(log, enforcementConfig, responseReceiverCache,
                liveSignalPub, getContext(), thingsShardRegion, system);
        smartChannelDispatching = new SupervisorSmartChannelDispatching(log, thingPersistenceActorSelection,
                liveChannelDispatching);
    }

    /**
     * Props for creating a {@code ThingSupervisorActor}.
     * <p>
     * Exceptions in the child are handled with a supervision strategy that stops the child
     * for {@link ActorKilledException}'s and escalates all others.
     * </p>
     *
     * @param pubSubMediator the pub/sub mediator ActorRef to required for the creation of the ThingEnforcerActor.
     * @param distributedPubThingEventsForTwin distributed-pub access for publishing thing events on "twin" channel.
     * @param liveSignalPub distributed-pub access for "live" channel.
     * @param propsFactory factory for creating Props to be used for creating
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @param policyEnforcerProvider used to load the policy enforcer.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the thing.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, null,
                distributedPubThingEventsForTwin, liveSignalPub, propsFactory, null, blockedNamespaces,
                policyEnforcerProvider, mongoReadJournal);
    }

    /**
     * Props for creating a {@code ThingSupervisorActor} inside of unit tests.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion,
                distributedPubThingEventsForTwin, liveSignalPub, propsFactory, null, blockedNamespaces,
                policyEnforcerProvider, mongoReadJournal);
    }

    /**
     * Props for creating a {@code ThingSupervisorActor} inside of unit tests.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final DistributedPub<ThingEvent<?>> distributedPubThingEventsForTwin,
            final LiveSignalPub liveSignalPub,
            final ActorRef thingsPersistenceActor,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final MongoReadJournal mongoReadJournal) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion,
                distributedPubThingEventsForTwin, liveSignalPub, null, thingsPersistenceActor, blockedNamespaces,
                policyEnforcerProvider, mongoReadJournal);
    }

    @Override
    protected CompletionStage<Object> askEnforcerChild(final Signal<?> signal) {

        if (signal instanceof ThingCommandResponse<?> thingCommandResponse &&
                CommandResponse.isLiveCommandResponse(thingCommandResponse)) {

            return signal.getDittoHeaders().getCorrelationId()
                    .map(responseReceiverCache::get)
                    .map(future -> future
                            .thenApply(responseReceiverEntry -> responseReceiverEntry
                                    .map(ResponseReceiverCache.ResponseReceiverCacheEntry::authorizationContext)
                                    .orElse(null)
                            )
                            .thenApply(authorizationContext ->
                                    replaceAuthContext(thingCommandResponse, authorizationContext)
                            )
                            .thenCompose(super::askEnforcerChild)
                    )
                    .orElseGet(() -> super.askEnforcerChild(thingCommandResponse).toCompletableFuture());
        } else {
            return super.askEnforcerChild(signal);
        }
    }

    /**
     * Replaces the {@link AuthorizationContext} in the headers of the passed {@code response}.
     *
     * @param response the ThingCommandResponse to replace the authorization context in.
     * @param authorizationContext the new authorization context to inject in the headers of the passed {@code response}
     * @return the modified thing command response.
     */
    @SuppressWarnings("unchecked")
    static <T extends ThingCommandResponse<?>> T replaceAuthContext(final T response,
            @Nullable final AuthorizationContext authorizationContext) {

        if (null != authorizationContext) {
            return (T) response.setDittoHeaders(response.getDittoHeaders()
                    .toBuilder()
                    .authorizationContext(authorizationContext)
                    .build());
        } else {
            return response;
        }
    }

    @Override
    protected CompletionStage<TargetActorWithMessage> getTargetActorForSendingEnforcedMessageTo(final Object message,
            final boolean shouldSendResponse, final ActorRef sender) {
        if (message instanceof CommandResponse<?> commandResponse &&
                CommandResponse.isLiveCommandResponse(commandResponse)) {

            return liveChannelDispatching.dispatchGlobalLiveCommandResponse(commandResponse);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                Signal.isChannelSmart(thingQueryCommand)) {

            return smartChannelDispatching.dispatchSmartChannelThingQueryCommand(thingQueryCommand, sender);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                Command.isLiveCommand(thingQueryCommand)) {

            return liveChannelDispatching.dispatchLiveChannelThingQueryCommand(thingQueryCommand,
                    liveChannelDispatching::prepareForPubSubPublishing);
        } else if (message instanceof Signal<?> signal &&
                (Command.isLiveCommand(signal) || Event.isLiveEvent(signal))) {

            return liveChannelDispatching.dispatchLiveSignal(signal, sender);
        } else {

            return super.getTargetActorForSendingEnforcedMessageTo(message, shouldSendResponse, sender);
        }
    }

    @Override
    protected CompletionStage<Object> modifyTargetActorCommandResponse(final Signal<?> enforcedSignal,
            final Object persistenceCommandResponse) {
        return Source.single(new CommandResponsePair<Signal<?>, Object>(enforcedSignal, persistenceCommandResponse))
                .flatMapConcat(pair -> {
                    if (pair.command() instanceof RetrieveThing retrieveThing &&
                            SupervisorInlinePolicyEnrichment.shouldRetrievePolicyWithThing(retrieveThing) &&
                            pair.response() instanceof RetrieveThingResponse retrieveThingResponse) {
                        return inlinePolicyEnrichment.enrichPolicy(retrieveThing, retrieveThingResponse)
                                .map(Object.class::cast);
                    } else if (RollbackCreatedPolicy.shouldRollbackBasedOnTargetActorResponse(pair.command(), pair.response())) {
                        final CompletableFuture<Object> responseF = new CompletableFuture<>();
                        getSelf().tell(RollbackCreatedPolicy.of(pair.command(), pair.response(), responseF), getSelf());
                        return Source.completionStage(responseF);
                    } else {
                        return Source.single(pair.response());
                    }
                })
                .toMat(Sink.head(), Keep.right())
                .run(materializer);
    }

    @Override
    protected CompletableFuture<Object> handleTargetActorAndEnforcerException(final Signal<?> signal, final Throwable throwable) {
        if (RollbackCreatedPolicy.shouldRollbackBasedOnException(signal, throwable)) {
            log.withCorrelationId(signal)
                    .info("Target actor exception received: <{}>. " +
                            "Sending RollbackCreatedPolicy msg to self, potentially rolling back a created policy.",
                            throwable.getClass().getSimpleName());
            final CompletableFuture<Object> responseFuture = new CompletableFuture<>();
            getSelf().tell(RollbackCreatedPolicy.of(signal, throwable, responseFuture), getSelf());
            return responseFuture;
        } else {
            log.withCorrelationId(signal)
                    .debug("Target actor exception received: <{}>", throwable.getClass().getSimpleName());
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private void handleRollbackCreatedPolicy(final RollbackCreatedPolicy rollback) {
        final String correlationId = rollback.initialCommand().getDittoHeaders().getCorrelationId()
                .orElse("unexpected:" + UUID.randomUUID());
        if (policyCreatedEvent != null) {
            log.withCorrelationId(rollback.initialCommand())
                    .warning("Rolling back created policy as consequence of received RollbackCreatedPolicy " +
                            "message: {}", rollback);
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(correlationId)
                    .putHeader(DittoHeaderDefinition.DITTO_SUDO.getKey(), "true")
                    .build();
            final DeletePolicy deletePolicy = DeletePolicy.of(policyCreatedEvent.policyId(), dittoHeaders);
            AskWithRetry.askWithRetry(policiesShardRegion, deletePolicy,
                    enforcementConfig.getAskWithRetryConfig(),
                    getContext().system(), response -> {
                        log.withCorrelationId(rollback.initialCommand())
                                .info("Policy <{}> deleted after rolling back it's creation. " +
                                        "Policies shard region response: <{}>", deletePolicy.getEntityId(), response);
                        rollback.completeInitialResponse();
                        return response;
                    }).exceptionally(throwable -> {
                log.withCorrelationId(rollback.initialCommand())
                        .error(throwable, "Failed to rollback Policy Create");
                rollback.completeInitialResponse();
                return null;
            });

        } else {
            log.withCorrelationId(rollback.initialCommand())
                    .debug("Not initiating policy rollback as none was created.");
            rollback.completeInitialResponse();
        }
        policyCreatedEvent = null;
    }

    @Override
    protected ThingId getEntityId() throws Exception {
        return ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    @Override
    protected Props getPersistenceActorProps(final ThingId entityId) {
        assert thingPersistenceActorPropsFactory != null;
        return thingPersistenceActorPropsFactory.props(entityId, mongoReadJournal, distributedPubThingEventsForTwin,
                searchShardRegionProxy);
    }

    @Override
    protected Props getPersistenceEnforcerProps(final ThingId entityId) {
        final ActorSystem system = getContext().getSystem();
        final ThingEnforcement thingEnforcement =
                new ThingEnforcement(policiesShardRegion, system, enforcementConfig);

        return ThingEnforcerActor.props(entityId, thingEnforcement, enforcementConfig.getAskWithRetryConfig(),
                policiesShardRegion, thingsShardRegion, policyEnforcerProvider);
    }

    @Override
    protected boolean shouldSendResponse(final WithDittoHeaders withDittoHeaders) {
        return withDittoHeaders.getDittoHeaders().isResponseRequired() ||
                withDittoHeaders.getDittoHeaders().getAcknowledgementRequests()
                        .stream()
                        .anyMatch(ar -> DittoAcknowledgementLabel.TWIN_PERSISTED.equals(ar.getLabel()));
    }

    @Override
    protected ShutdownBehaviour getShutdownBehaviour(final ThingId entityId) {
        return ShutdownBehaviour.fromId(entityId, pubSubMediator, getSelf());
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> getUnavailableExceptionBuilder(@Nullable final ThingId entityId) {
        return ThingUnavailableException.newBuilder(
                Objects.requireNonNullElseGet(entityId, () -> ThingId.of("UNKNOWN:ID")));
    }

    @Override
    protected ExponentialBackOffConfig getExponentialBackOffConfig() {
        return DittoThingsConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getThingConfig()
                .getSupervisorConfig()
                .getExponentialBackOffConfig();
    }

    @Override
    protected LocalAskTimeoutConfig getLocalAskTimeoutConfig() {
        return DittoThingsConfig.of(DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()))
                .getThingConfig()
                .getSupervisorConfig()
                .getLocalAskTimeoutConfig();
    }

    @Override
    protected void stopShardedActor(final StopShardedActor trigger) {
        super.stopShardedActor(trigger);
        if (getOpCounter() > 0) {
            getTimers().startSingleTimer(Control.SHUTDOWN_TIMEOUT, Control.SHUTDOWN_TIMEOUT, shutdownTimeout);
        }
    }

    @Override
    protected Receive activeBehaviour(final Runnable matchProcessNextTwinMessageBehavior,
            final FI.UnitApply<Object> matchAnyBehavior) {
        return ReceiveBuilder.create()
                .matchEquals(Control.SHUTDOWN_TIMEOUT, this::shutdownActor)
                .match(ThingPolicyCreated.class, msg -> {
                    log.withCorrelationId(msg.dittoHeaders()).info("ThingPolicyCreated msg received: <{}>", msg.policyId());
                    this.policyCreatedEvent = msg;
                }).match(RollbackCreatedPolicy.class, this::handleRollbackCreatedPolicy)
                .build()
                .orElse(super.activeBehaviour(matchProcessNextTwinMessageBehavior, matchAnyBehavior));
    }

    private void shutdownActor(final Control shutdown) {
        log.warning("Shutdown timeout <{}> reached; aborting <{}> ops and stopping myself", shutdownTimeout,
                getOpCounter());
        getContext().stop(getSelf());
    }

    private record CommandResponsePair<C, R>(C command, R response) {}

    private enum Control {
        SHUTDOWN_TIMEOUT
    }

    /**
     * Used from the {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor} to signal itself
     * to delete an already created policy because of a failure in creating a thing
     * @param initialCommand the initial command that triggered the creation of a thing and policy
     * @param response the response from the thing persistence actor
     * @param responseFuture a future that when completed with the response from the thing persistence actor the response
     * will be sent to the initial sender.
     */
    private record RollbackCreatedPolicy(Signal<?> initialCommand, Object response, CompletableFuture<Object> responseFuture) {

        /**
         * Initialises an instance of {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor.RollbackCreatedPolicy}
         * @param initialCommand the initial initialCommand that triggered the creation of a thing and policy
         * @param response the response from the thing persistence actor
         * @param responseFuture a future that when completed with the response from the thing persistence actor the response
         * will be sent to the initial sender.
         * @return an instance of {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor.RollbackCreatedPolicy}
         */
        public static RollbackCreatedPolicy of(final Signal<?> initialCommand, final Object response,
                final CompletableFuture<Object> responseFuture) {
            return new RollbackCreatedPolicy(initialCommand, response, responseFuture);
        }

        /**
         * Evaluates if a failure in the creation of a thing should lead to deleting of that thing's policy.
         * Should be used only to evaluate exceptions from the target actor not the enforcement actor.
         * @param command the initial command.
         * @param response the response from the {@link org.eclipse.ditto.things.service.persistence.actors.ThingPersistenceActor}.
         * @return if the thing's policy is to be deleted.
         */
        private static boolean shouldRollbackBasedOnTargetActorResponse(final Signal<?> command, @Nullable final Object response) {
            return command instanceof CreateThing && response instanceof DittoRuntimeException;
        }

        /**
         * Evaluates if a failure in the creation of a thing should lead to deleting of that thing's policy.
         * @param signal the initial signal.
         * @param throwable the throwable received from the Persistence Actor
         * @return if the thing's policy is to be deleted.
         */
        private static boolean shouldRollbackBasedOnException(final Signal<?> signal, @Nullable final Throwable throwable) {
                return signal instanceof CreateThing && ((throwable instanceof CompletionException ce1 &&  ce1.getCause() instanceof ThingUnavailableException)
                        || throwable instanceof AskTimeoutException
                        || (throwable instanceof CompletionException ce && ce.getCause() instanceof AskTimeoutException)
                );
        }

        /**
         * Completes the responseFuture with the response which in turn should send the Persistence actor response to
         * the initial sender. If an additional exception occurs during policy rollback the responseFuture will be
         * completed with that exception and adding the target actor exception as suppressed warning.
         *
         * @param throwable the additional optional exception occurred during the rollback process.
         */
        private void completeInitialResponse(@Nullable final Throwable throwable) {
            if (response instanceof Throwable t) {
                if (throwable != null) {
                    throwable.addSuppressed(t);
                    responseFuture.completeExceptionally(throwable);
                } else {
                    responseFuture.completeExceptionally(t);
                }
            } else {
                responseFuture.complete(response);
            }
        }

        /**
         * Completes the responseFuture with the response which in turn should send the Persistence actor response to
         * the initial sender.
         */
        private void completeInitialResponse() {
            completeInitialResponse(null);
        }
    }
}
