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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.service.actors.ShutdownBehaviour;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.enforcement.PreEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.enforcement.ThingEnforcement;

import akka.NotUsed;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
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

    private static final Duration MIN_LIVE_TIMEOUT = Duration.ofSeconds(1L);
    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    private static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(ThingCommand::getEntityId, ThingCommand::getDittoHeaders);
    private static final AckExtractor<ThingEvent<?>> THING_EVENT_ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);
    private static final AckExtractor<MessageCommand<?, ?>> MESSAGE_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(MessageCommand::getEntityId, MessageCommand::getDittoHeaders);

    private final ActorRef pubSubMediator;
    private final ActorRef policiesShardRegion;
    private final LiveSignalPub liveSignalPub;
    @Nullable private final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory;
    private final DefaultEnforcementConfig enforcementConfig;
    private final Materializer materializer;
    private final ResponseReceiverCache responseReceiverCache;

    @SuppressWarnings("unused")
    private ThingSupervisorActor(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            @Nullable final ThingPersistenceActorPropsFactory thingPersistenceActorPropsFactory,
            @Nullable final ActorRef thingPersistenceActorRef,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PreEnforcer<Signal<?>> preEnforcer) {

        super(blockedNamespaces, preEnforcer);

        this.pubSubMediator = pubSubMediator;
        this.policiesShardRegion = policiesShardRegion;
        this.liveSignalPub = liveSignalPub;
        this.thingPersistenceActorPropsFactory = thingPersistenceActorPropsFactory;
        this.persistenceActorChild = thingPersistenceActorRef;
        enforcementConfig = DefaultEnforcementConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        materializer = Materializer.createMaterializer(getContext());
        responseReceiverCache = ResponseReceiverCache.lookup(getContext().getSystem());
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
     * @param preEnforcer the PreEnforcer to apply as extension mechanism of the enforcement.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            final ThingPersistenceActorPropsFactory propsFactory,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PreEnforcer<Signal<?>> preEnforcer) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion, liveSignalPub,
                propsFactory, null, blockedNamespaces, preEnforcer);
    }

    /**
     * Props for creating a {@code ThingSupervisorActor} inside of unit tests.
     */
    public static Props props(final ActorRef pubSubMediator,
            final ActorRef policiesShardRegion,
            final LiveSignalPub liveSignalPub,
            final ActorRef thingsPersistenceActor,
            @Nullable final BlockedNamespaces blockedNamespaces,
            final PreEnforcer<Signal<?>> preEnforcer) {

        return Props.create(ThingSupervisorActor.class, pubSubMediator, policiesShardRegion, liveSignalPub,
                null, thingsPersistenceActor, blockedNamespaces, preEnforcer);
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

        if (message instanceof Signal<?> enforcedSignal && CommandResponse.isLiveCommandResponse(enforcedSignal)) {
            return dispatchGlobalLiveCommandResponse((CommandResponse<?>) enforcedSignal);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                ThingCommand.isChannelSmart(thingQueryCommand)) {
            return dispatchSmartChannelThingQueryCommand(sender, thingQueryCommand);
        } else if (message instanceof ThingQueryCommand<?> thingQueryCommand &&
                Command.isLiveCommand(thingQueryCommand)) {
            return dispatchLiveChannelThingQueryCommand(sender, thingQueryCommand, this::createLiveQueryCommandTarget);
        } else if (message instanceof Signal<?> signal &&
                (Command.isLiveCommand(signal) || Event.isLiveEvent(signal))) {
            return dispatchLiveSignal(sender, signal);
        } else if (message instanceof Signal<?> enforcedSignal && !Command.isLiveCommand(enforcedSignal)) {
            return super.getTargetActorForSendingEnforcedMessageTo(message, sender);
        }

        log.withCorrelationId(message instanceof WithDittoHeaders wdh ? wdh.getDittoHeaders() : null)
                .error("Could not determine target actor for message: {}", message);
        return CompletableFuture.completedStage(null);
    }

    private CompletionStage<TargetActorWithMessage> dispatchGlobalLiveCommandResponse(
            final CommandResponse<?> commandResponse) {

        return WithDittoHeaders.getCorrelationId(commandResponse).map(correlationId ->
                dispatchLiveCommandResponse(commandResponse, correlationId)
        ).orElseGet(() -> CompletableFuture.completedStage(null));
    }

    private CompletionStage<TargetActorWithMessage> dispatchLiveCommandResponse(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId) {

        final CompletionStage<TargetActorWithMessage> result;
        if (enforcementConfig.isDispatchLiveResponsesGlobally()) {
            result = returnCommandResponseContext(liveResponse, correlationId);
        } else {
            log.withCorrelationId(liveResponse)
                    .info("Got live response when global dispatching is inactive: <{}>", liveResponse.getType());
            result = CompletableFuture.completedFuture(null);
        }
        return result;
    }

    private CompletionStage<TargetActorWithMessage> dispatchSmartChannelThingQueryCommand(final ActorRef sender,
            final ThingQueryCommand<?> thingQueryCommand) {

        return initSmartChannelSelection(thingQueryCommand)
                .thenCompose(twinQueryCommandResponse -> {
                    if (SmartChannelSelection.shouldAttemptLiveChannel(thingQueryCommand, twinQueryCommandResponse)) {
                        // perform conversion + publishing of live command
                        final ThingQueryCommand<?> liveCommand = SmartChannelSelection.toLiveCommand(thingQueryCommand);
                        return dispatchLiveChannelThingQueryCommand(sender, liveCommand,
                                (command, receiver) ->
                                        createLiveQueryCommandTarget(command, receiver, response ->
                                                SmartChannelSelection.getFallbackResponseCaster(liveCommand,
                                                                twinQueryCommandResponse
                                                        ).apply(response)
                                        )
                        );
                    } else {
                        // directly respond with twin response to sender
                        return CompletableFuture.completedStage(new TargetActorWithMessage(
                                sender,
                                twinQueryCommandResponse,
                                Duration.ZERO,
                                Function.identity()
                        ));
                    }
                });
    }

    private CompletionStage<ThingQueryCommandResponse<?>> initSmartChannelSelection(
            final ThingQueryCommand<?> thingQueryCommand) {

        final ThingQueryCommand<?> twinQueryCommand = SmartChannelSelection.ensureTwinChannel(thingQueryCommand);
        return Patterns.ask(persistenceActorChild, twinQueryCommand, DEFAULT_LOCAL_ASK_TIMEOUT)
                .thenCompose(response -> modifyTargetActorCommandResponse(thingQueryCommand, response))
                .thenApply(response ->
                        SmartChannelSelection.handleSmartChannelTwinResponse(thingQueryCommand, response));
    }

    private CompletionStage<TargetActorWithMessage> dispatchLiveChannelThingQueryCommand(final ActorRef sender,
            final ThingQueryCommand<?> thingQueryCommand,
            final BiFunction<ThingQueryCommand<?>, ActorRef, TargetActorWithMessage> responseHandler) {

        if (enforcementConfig.shouldDispatchGlobally(thingQueryCommand)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(thingQueryCommand,
                    conflictFreeCommand -> createLiveResponseReceiverActor(conflictFreeCommand, sender),
                    responseHandler
            );
        } else {
            final var receiver = createLiveResponseReceiverActor(thingQueryCommand, sender);
            return CompletableFuture.completedStage(responseHandler.apply(thingQueryCommand, receiver));
        }
    }

    private CompletionStage<TargetActorWithMessage> dispatchLiveSignal(final ActorRef sender, final Signal<?> signal) {

        final DistributedPubWithMessage distributedPubWithMessage = selectLiveSignalPublisher(signal);
        if (enforcementConfig.shouldDispatchGlobally(signal)) {
            return responseReceiverCache.insertResponseReceiverConflictFree(signal,
                            newSignal -> sender,
                            (newSignal, receiver) -> {
                                log.withCorrelationId(newSignal)
                                        .info("Publishing message to pub-sub: <{}>", newSignal.getType());
                                if (newSignal.equals(signal)) {
                                    return distributedPubWithMessage;
                                } else {
                                    return selectLiveSignalPublisher(newSignal);
                                }
                            })
                    .thenApply(distributedPub -> new TargetActorWithMessage(
                            distributedPub.pub().getPublisher(),
                            distributedPub.wrappedSignalForPublication(),
                            distributedPub.signal().getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT),
                            response -> handleEncounteredAskTimeoutsAsCommandTimeoutException(
                                    signal, distributedPub, response)
                    ));
        } else {
            log.withCorrelationId(signal)
                    .debug("Publish message to pub-sub: <{}>", signal);
            return CompletableFuture.completedStage(new TargetActorWithMessage(
                    distributedPubWithMessage.pub().getPublisher(),
                    distributedPubWithMessage.wrappedSignalForPublication(),
                    signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT),
                    Function.identity()
            ));
        }
    }

    private Object handleEncounteredAskTimeoutsAsCommandTimeoutException(final Signal<?> signal,
            final DistributedPubWithMessage distributedPub,
            final Object response) {

        if (response instanceof Throwable t) {
            Throwable throwable = t;
            if (t instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }

            if (throwable instanceof AskTimeoutException askTimeoutException) {
                return CommandTimeoutException.newBuilder(
                                distributedPub.signal().getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT)
                        )
                        .cause(askTimeoutException)
                        .dittoHeaders(signal.getDittoHeaders())
                        .build();
            } else {
                return response;
            }
        } else {
            return response;
        }
    }

    private TargetActorWithMessage createLiveQueryCommandTarget(
            final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef receiver) {

        return createLiveQueryCommandTarget(thingQueryCommand, receiver, Function.identity());
    }

    private TargetActorWithMessage createLiveQueryCommandTarget(
            final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef receiver,
            final Function<Object, Object> responseOrErrorConverter) {

        final var startTime = Instant.now();
        final var timeout = getAdjustedTimeout(thingQueryCommand, startTime);
        final var signalWithAdjustedTimeout = adjustTimeout(thingQueryCommand, timeout);
        final var pub = liveSignalPub.command();
        final var publish =
                pub.wrapForPublicationWithAcks(signalWithAdjustedTimeout, THING_COMMAND_ACK_EXTRACTOR);

        return new TargetActorWithMessage(
                receiver,
                publish,
                timeout,
                responseOrErrorConverter
        );
    }

    private ActorRef createLiveResponseReceiverActor(final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef sender) {

        final var pub = liveSignalPub.command();
        final var props = LiveResponseAndAcknowledgementForwarder.props(thingQueryCommand, pub.getPublisher(),
                sender);
        // and start the actor as child of this supervisor!
        return getContext().actorOf(props);
    }

    private static Duration getAdjustedTimeout(final Signal<?> signal, final Instant startTime) {

        final var baseTimeout = getLiveSignalTimeout(signal);
        final var adjustedTimeout = baseTimeout.minus(Duration.between(startTime, Instant.now()));
        return adjustedTimeout.minus(MIN_LIVE_TIMEOUT).isNegative() ? MIN_LIVE_TIMEOUT : adjustedTimeout;
    }

    static Duration getLiveSignalTimeout(final Signal<?> signal) {
        return signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
    }

    private static ThingCommand<?> adjustTimeout(final ThingCommand<?> signal, final Duration adjustedTimeout) {

        return signal.setDittoHeaders(
                signal.getDittoHeaders()
                        .toBuilder()
                        .timeout(adjustedTimeout)
                        .build()
        );
    }

    private CompletionStage<TargetActorWithMessage> returnCommandResponseContext(
            final CommandResponse<?> liveResponse,
            final CharSequence correlationId) {

        return responseReceiverCache.get(correlationId)
                .thenApply(responseReceiverEntry -> {
                    final TargetActorWithMessage targetActorWithMessage;
                    if (responseReceiverEntry.isPresent()) {
                        final var receiver = responseReceiverEntry.get();
                        log.withCorrelationId(liveResponse)
                                .info("Responding with command response type <{}> to original sender <{}>",
                                        liveResponse.getType(), receiver);
                        targetActorWithMessage = new TargetActorWithMessage(receiver.sender(),
                                liveResponse,
                                getLiveSignalTimeout(liveResponse),
                                Function.identity()
                        );
                        responseReceiverCache.invalidate(correlationId);
                    } else {
                        log.withCorrelationId(liveResponse)
                                .warning("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(),
                                        correlationId);
                        targetActorWithMessage = null;
                    }
                    return targetActorWithMessage;
                });
    }

    private DistributedPubWithMessage selectLiveSignalPublisher(final Signal<?> enforcedSignal) {

        final var streamingType = StreamingType.fromSignal(enforcedSignal);
        if (streamingType.isPresent()) {
            switch (streamingType.get()) {
                case MESSAGES -> {
                    final DistributedPub<SignalWithEntityId<?>> pubM = liveSignalPub.message();
                    return new DistributedPubWithMessage(pubM,
                            wrapLiveSignal((MessageCommand<?, ?>) enforcedSignal, MESSAGE_COMMAND_ACK_EXTRACTOR, pubM),
                            enforcedSignal
                    );
                }
                case LIVE_EVENTS -> {
                    final DistributedPub<ThingEvent<?>> pubE = liveSignalPub.event();
                    return new DistributedPubWithMessage(pubE,
                            wrapLiveSignal((ThingEvent<?>) enforcedSignal, THING_EVENT_ACK_EXTRACTOR, pubE),
                            enforcedSignal
                    );
                }
                case LIVE_COMMANDS -> {
                    final DistributedPub<ThingCommand<?>> pubC = liveSignalPub.command();
                    return new DistributedPubWithMessage(pubC,
                            wrapLiveSignal((ThingCommand<?>) enforcedSignal, THING_COMMAND_ACK_EXTRACTOR, pubC),
                            enforcedSignal
                    );
                }
                default -> {
                    // empty
                }
            }
        }
        log.withCorrelationId(enforcedSignal)
                .warning("Ignoring unsupported signal: <{}>", enforcedSignal);
        throw UnsupportedSignalException.newBuilder(enforcedSignal.getType())
                .message("The sent command is not supported as live command")
                .dittoHeaders(enforcedSignal.getDittoHeaders())
                .build();
    }

    private <T extends Signal<?>, S extends T> Object wrapLiveSignal(final S signal,
            final AckExtractor<S> ackExtractor, final DistributedPub<T> pub) {

        return pub.wrapForPublicationWithAcks(signal, ackExtractor);
    }

    @Override
    protected CompletionStage<Object> modifyTargetActorCommandResponse(final Signal<?> enforcedSignal,
            final Object persistenceCommandResponse) {

        return Source.single(new CommandResponsePair<Signal<?>, Object>(enforcedSignal, persistenceCommandResponse))
                .flatMapConcat(pair -> {
                    if (pair.command() instanceof RetrieveThing retrieveThing &&
                            shouldRetrievePolicyWithThing(retrieveThing) &&
                            pair.response() instanceof RetrieveThingResponse retrieveThingResponse) {
                        return enrichPolicy(retrieveThing, retrieveThingResponse)
                                .map(Object.class::cast);
                    } else {
                        return Source.single(pair.response());
                    }
                })
                .toMat(Sink.head(), Keep.right())
                .run(materializer);
    }

    private Source<RetrieveThingResponse, NotUsed> enrichPolicy(final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse) {

        return retrievePolicyIdViaSudoRetrieveThing()
                .map(SudoRetrieveThingResponse::getThing)
                .map(Thing::getPolicyId)
                .map(optionalPolicyId -> optionalPolicyId.orElseThrow(() -> {
                    log.withCorrelationId(retrieveThing)
                            .warning("Found thing without policy ID. This should never be possible. " +
                                    "This is most likely a bug and should be fixed.");
                    return ThingNotAccessibleException.newBuilder(entityId)
                            .dittoHeaders(retrieveThing.getDittoHeaders())
                            .build();
                }))
                .map(policyId -> {
                    final var dittoHeadersWithoutPreconditionHeaders = retrieveThing.getDittoHeaders()
                            .toBuilder()
                            .removePreconditionHeaders()
                            .build();
                    return RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders);
                })
                .map(this::retrieveInlinedPolicyForThing)
                .flatMapConcat(Source::completionStage)
                .map(policyResponse -> {
                    if (policyResponse.isPresent()) {
                        final JsonObject inlinedPolicy = policyResponse.get()
                                .getPolicy()
                                .toInlinedJson(retrieveThing.getImplementedSchemaVersion(),
                                        FieldType.notHidden());

                        final JsonObject thingWithInlinedPolicy = retrieveThingResponse.getEntity()
                                .asObject()
                                .toBuilder()
                                .setAll(inlinedPolicy)
                                .build();
                        return retrieveThingResponse.setEntity(thingWithInlinedPolicy);
                    } else {
                        return retrieveThingResponse;
                    }
                });
    }

    private Source<SudoRetrieveThingResponse, NotUsed> retrievePolicyIdViaSudoRetrieveThing() {

        final CompletionStage<Object> askForThing =
                Patterns.ask(persistenceActorChild, SudoRetrieveThing.of(entityId,
                                JsonFieldSelector.newInstance("policyId"),
                                DittoHeaders.newBuilder()
                                        .correlationId("retrievePolicyIdViaSudoRetrieveThing-" + UUID.randomUUID())
                                        .build()
                        ), DEFAULT_LOCAL_ASK_TIMEOUT
                );
        return Source.completionStage(askForThing)
                .map(response -> {
                    if (response instanceof DittoRuntimeException dre) {
                        throw dre;
                    }
                    return response;
                })
                .divertTo(Sink.foreach(unexpectedResponseType ->
                                log.warning("Unexpected response type. Expected <{}>, but got <{}>.",
                                        SudoRetrieveThingResponse.class, unexpectedResponseType.getClass())),
                        response -> !(response instanceof SudoRetrieveThingResponse))
                .map(SudoRetrieveThingResponse.class::cast);
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param retrieveThing the RetrieveThing command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    private static boolean shouldRetrievePolicyWithThing(final RetrieveThing retrieveThing) {

        return retrieveThing.getSelectedFields()
                .filter(selector -> selector.getPointers()
                        .stream()
                        .anyMatch(jsonPointer -> jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrievePolicy retrievePolicy) {

        return AskWithRetry.askWithRetry(policiesShardRegion, retrievePolicy,
                        enforcementConfig.getAskWithRetryConfig(),
                        getContext().getSystem(),
                        response -> {
                            if (response instanceof RetrievePolicyResponse retrievePolicyResponse) {
                                return Optional.of(retrievePolicyResponse);
                            } else {
                                log.withCorrelationId(getCorrelationIdOrNull(response, retrievePolicy))
                                        .info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                                retrievePolicy.getEntityId(), entityId, response);
                                return Optional.<RetrievePolicyResponse>empty();
                            }
                        }
                ).exceptionally(error -> {
                    log.withCorrelationId(getCorrelationIdOrNull(error, retrievePolicy))
                            .error("Retrieving inlined policy after RetrieveThing", error);
                    return Optional.empty();
                });
    }

    @Override
    protected ThingId getEntityId() throws Exception {
        return ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8.name()));
    }

    @Override
    protected Props getPersistenceActorProps(final ThingId entityId) {
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

    @Nullable
    private static CharSequence getCorrelationIdOrNull(final Object signal, final WithDittoHeaders fallBackSignal) {

        final WithDittoHeaders withDittoHeaders;
        if (isWithDittoHeaders(signal)) {
            withDittoHeaders = (WithDittoHeaders) signal;
        } else {
            withDittoHeaders = fallBackSignal;
        }
        final var dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.getCorrelationId().orElse(null);
    }

    private static boolean isWithDittoHeaders(final Object o) {
        return o instanceof WithDittoHeaders;
    }

    private record CommandResponsePair<C, R>(C command, R response) {}
}
