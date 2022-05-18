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
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.persistentactors.TargetActorWithMessage;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.enforcement.ThingEnforcement;

import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.Materializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

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
    private final LiveSignalPub liveSignalPub;
    @Nullable private final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory;
    private final DefaultEnforcementConfig enforcementConfig;
    private final Materializer materializer;
    private final ResponseReceiverCache responseReceiverCache;

    private final SupervisorInlinePolicyEnrichment inlinePolicyEnrichment;
    private final SupervisorLiveChannelDispatching liveChannelDispatching;
    private final SupervisorSmartChannelDispatching smartChannelDispatching;

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            @Nullable final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory,
            @Nullable final ActorRef thingPersistenceActorRef,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        super(blockedNamespaces);

        this.pubSubMediator = pubSubMediator;
        this.policiesShardRegion = policiesShardRegion;
        this.liveSignalPub = liveSignalPub;
        this.thingPersistenceActorPropsFactory = thingPersistenceActorPropsFactory;
        persistenceActorChild = thingPersistenceActorRef;
        enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        materializer = Materializer.createMaterializer(getContext());
        responseReceiverCache = ResponseReceiverCache.lookup(getContext().getSystem());

        final ActorSelection thingPersistenceActorSelection;
        if (null != persistenceActorChild) {
            thingPersistenceActorSelection = getContext().actorSelection(persistenceActorChild.path());
        } else {
            // we use a ActorSelection so that we
            // a) do not have to have the persistence actor initialized at this point
            // b) must not react on recreation of the persistence actor by recreating the classes using it
            thingPersistenceActorSelection = getContext().actorSelection(PERSISTENCE_ACTOR_NAME);
        }

        try {
            inlinePolicyEnrichment = new SupervisorInlinePolicyEnrichment(getContext().getSystem(), log, getEntityId(),
                    thingPersistenceActorSelection, policiesShardRegion, enforcementConfig);
        } catch (final Exception e) {
            throw new IllegalStateException("Entity Id could not be retrieved", e);
        }
        liveChannelDispatching = new SupervisorLiveChannelDispatching(log, enforcementConfig, responseReceiverCache,
                liveSignalPub, getContext());
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
     * @param policiesShardRegion the shard region of the "policies" shard in order to e.g. load policies.
     * @param liveSignalPub distributed-pub access.
     * @param propsFactory factory for creating Props to be used for creating
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion, liveSignalPub,
                propsFactory, null, blockedNamespaces);
    }

    /**
     * Props for creating a {@code ThingSupervisorActor} inside of unit tests.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            final ActorRef thingsPersistenceActor,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion, liveSignalPub,
                null, thingsPersistenceActor, blockedNamespaces);
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
            final ActorRef sender) {

        if (message instanceof CommandResponse<?> commandResponse &&
                CommandResponse.isLiveCommandResponse(commandResponse)) {

            return liveChannelDispatching.dispatchGlobalLiveCommandResponse(commandResponse);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                ThingCommand.isChannelSmart(thingQueryCommand)) {

            return smartChannelDispatching.dispatchSmartChannelThingQueryCommand(thingQueryCommand, sender);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                Command.isLiveCommand(thingQueryCommand)) {

            return liveChannelDispatching.dispatchLiveChannelThingQueryCommand(thingQueryCommand, sender,
                            liveChannelDispatching::prepareForPubSubPublishing);
        } else if (message instanceof Signal<?> signal &&
                (Command.isLiveCommand(signal) || Event.isLiveEvent(signal))) {

            return liveChannelDispatching.dispatchLiveSignal(signal, sender);
        } else {

            return super.getTargetActorForSendingEnforcedMessageTo(message, sender);
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
                    } else {
                        return Source.single(pair.response());
                    }
                })
                .toMat(Sink.head(), Keep.right())
                .run(materializer);
    }

    @Override
    protected ThingId getEntityId() throws Exception {
        return ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final ThingId entityId) {
        assert thingPersistenceActorPropsFactory != null;
        return thingPersistenceActorPropsFactory.props(entityId, liveSignalPub.event());
    }

    @Override
    protected Props getPersistenceEnforcerProps(final ThingId entityId) {
        final ActorContext actorContext = getContext();
        final ActorSystem actorSystem = actorContext.getSystem();

        final ThingEnforcement thingEnforcement = new ThingEnforcement(
                actorSystem,
                policiesShardRegion,
                creationRestrictionEnforcer,
                enforcementConfig
        );

        return ThingEnforcerActor.props(entityId, thingEnforcement, pubSubMediator, blockedNamespaces);
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

    private record CommandResponsePair<C, R>(C command, R response) {}
}
