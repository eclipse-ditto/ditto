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
package org.eclipse.ditto.concierge.service.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.policies.api.Permission.WRITE;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.LiveSignalPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.MessageFormatInvalidException;
import org.eclipse.ditto.messages.model.MessageSendNotAllowedException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessage;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcement<SignalWithEntityId<?>> {

    private static final AckExtractor<ThingCommand<?>> THING_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(ThingCommand::getEntityId, ThingCommand::getDittoHeaders);
    private static final AckExtractor<ThingEvent<?>> THING_EVENT_ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);
    private static final AckExtractor<MessageCommand<?, ?>> MESSAGE_COMMAND_ACK_EXTRACTOR =
            AckExtractor.of(MessageCommand::getEntityId, MessageCommand::getDittoHeaders);

    private final EnforcerRetriever<Enforcer> enforcerRetriever;
    private final LiveSignalPub liveSignalPub;

    private LiveSignalEnforcement(final Contextual<SignalWithEntityId<?>> context,
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
            final LiveSignalPub liveSignalPub) {

        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        enforcerRetriever = PolicyEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache);
        this.liveSignalPub = liveSignalPub;
    }

    /**
     * {@link EnforcementProvider} for {@link LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<SignalWithEntityId<?>> {

        private final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache;
        private final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache;
        private final LiveSignalPub liveSignalPub;

        /**
         * Constructor.
         *
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param liveSignalPub distributed-pub access for live signal publication
         */
        public Provider(final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> thingIdCache,
                final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache,
                final LiveSignalPub liveSignalPub) {

            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.liveSignalPub = liveSignalPub;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
        public Class<SignalWithEntityId<?>> getCommandClass() {
            return (Class) SignalWithEntityId.class;
        }

        @Override
        public boolean isApplicable(final SignalWithEntityId<?> signal) {
            return LiveSignalEnforcement.isLiveSignal(signal);
        }

        @Override
        public AbstractEnforcement<SignalWithEntityId<?>> createEnforcement(final Contextual<SignalWithEntityId<?>> context) {
            return new LiveSignalEnforcement(context, thingIdCache, policyEnforcerCache, liveSignalPub);
        }

    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        final SignalWithEntityId<?> liveSignal = signal();
        return enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(liveSignal, enforcerEntry);
            } catch (final RuntimeException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final SignalWithEntityId<?> liveSignal,
            final Entry<Enforcer> enforcerEntry) {

        final Optional<String> correlationIdOpt = liveSignal.getDittoHeaders().getCorrelationId();
        if (enforcerEntry.exists() && correlationIdOpt.isPresent()) {
            final Enforcer enforcer = enforcerEntry.getValueOrThrow();

            if (liveSignal instanceof SendClaimMessage) {
                // claim messages require no enforcement, publish them right away:
                final SendClaimMessage<?> sendClaimMessage = (SendClaimMessage<?>) liveSignal;
                return publishMessageCommand(sendClaimMessage, enforcer);
            } else if (liveSignal instanceof CommandResponse) {
                return enforceLiveCommandResponse((CommandResponse<?>) liveSignal, correlationIdOpt.get(), enforcer);
            } else {
                final Optional<StreamingType> streamingType = StreamingType.fromSignal(liveSignal);
                if (streamingType.isPresent()) {
                    return enforceLiveSignal(streamingType.get(), liveSignal, enforcer);
                } else {
                    log().error("Unsupported Signal in LiveSignalEnforcement: <{}>", liveSignal);
                    throw GatewayInternalErrorException.newBuilder()
                            .dittoHeaders(liveSignal.getDittoHeaders())
                            .build();
                }
            }
        } else {
            // drop live command to nonexistent things and respond with error.
            log(liveSignal).info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer " +
                            "could be looked up! Answering with ThingNotAccessibleException.", liveSignal.getType(),
                    liveSignal.getEntityId());
            throw ThingNotAccessibleException.newBuilder(ThingId.of(entityId().getId()))
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveCommandResponse(
            final CommandResponse<?> liveResponse, final String correlationId, final Enforcer enforcer) {

        final Optional<Cache<String, ActorRef>> responseReceiversOptional = context.getResponseReceivers();
        if (responseReceiversOptional.isPresent()) {
            final Cache<String, ActorRef> responseReceivers = responseReceiversOptional.get();
            return returnWithMessageToReceiver(responseReceivers, liveResponse, correlationId, enforcer);
        } else {
            if (log().isDebugEnabled()) {
                log().debug("Got live response when global dispatching is inactive: <{}>", liveResponse);
            } else {
                log().info("Got live response when global dispatching is inactive: <{}> with correlation ID <{}>",
                        liveResponse.getType(),
                        liveResponse.getDittoHeaders().getCorrelationId().orElse(""));
            }
            return CompletableFuture.completedFuture(withMessageToReceiver(null, null));
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> returnWithMessageToReceiver(
            final Cache<String, ActorRef> responseReceivers, final CommandResponse<?> liveResponse,
            final String correlationId, final Enforcer enforcer) {
        return responseReceivers.get(correlationId).thenApply(responseReceiverEntry -> {
            if (responseReceiverEntry.isPresent()) {
                responseReceivers.invalidate(correlationId);
                final ActorRef responseReceiver = responseReceiverEntry.get();
                final CommandResponse<?> response;
                if (liveResponse instanceof ThingQueryCommandResponse) {
                    response = ThingCommandEnforcement.buildJsonViewForThingQueryCommandResponse(
                            (ThingQueryCommandResponse<?>) liveResponse, enforcer);
                } else {
                    response = liveResponse;
                }

                log().debug("Scheduling CommandResponse <{}> to original sender <{}>", liveResponse,
                        responseReceiver);
                return withMessageToReceiver(response, responseReceiver);
            } else {
                if (log().isDebugEnabled()) {
                    log().debug("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(), liveResponse);
                } else {
                    log().info("Got <{}> with unknown correlation ID: <{}>", liveResponse.getType(), correlationId);
                }
                return withMessageToReceiver(null, null);
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveSignal(final StreamingType streamingType,
            final Signal<?> liveSignal, final Enforcer enforcer) {

        switch (streamingType) {
            case MESSAGES:
                return enforceMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            case LIVE_EVENTS:
                return enforceLiveEvent(liveSignal, enforcer);
            case LIVE_COMMANDS:

                ThingCommandEnforcement.authorizeByPolicyOrThrow(enforcer, (ThingCommand<?>) liveSignal);
                final ThingCommand<?> withReadSubjects =
                        addEffectedReadSubjectsToThingSignal((ThingCommand<?>) liveSignal, enforcer);
                log(withReadSubjects).info("Live Command was authorized: <{}>", withReadSubjects);
                return publishLiveSignal(withReadSubjects, THING_COMMAND_ACK_EXTRACTOR, liveSignalPub.command());
            default:
                log(liveSignal).warning("Ignoring unsupported command signal: <{}>", liveSignal);
                throw UnknownCommandException.newBuilder(liveSignal.getName())
                        .message("The sent command is not supported as live command")
                        .dittoHeaders(liveSignal.getDittoHeaders())
                        .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveEvent(final Signal<?> liveSignal,
            final Enforcer enforcer) {

        final boolean authorized = enforcer.hasUnrestrictedPermissions(
                PoliciesResourceType.thingResource(liveSignal.getResourcePath()),
                liveSignal.getDittoHeaders().getAuthorizationContext(), WRITE);

        if (authorized) {
            log(liveSignal).info("Live Event was authorized: <{}>", liveSignal);
            final ThingEvent<?> withReadSubjects =
                    addEffectedReadSubjectsToThingSignal((ThingEvent<?>) liveSignal, enforcer);
            return publishLiveSignal(withReadSubjects, THING_EVENT_ACK_EXTRACTOR, liveSignalPub.event());
        } else {
            log(liveSignal).info("Live Event was NOT authorized: <{}>", liveSignal);
            throw EventSendNotAllowedException.newBuilder(((ThingEvent<?>) liveSignal).getEntityId())
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    /**
     * Tests whether a signal is applicable for live signal enforcement.
     *
     * @param signal the signal to test.
     * @return whether the signal belongs to the live channel.
     */
    static boolean isLiveSignal(final Signal<?> signal) {
        return StreamingType.isLiveSignal(signal);
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {

        if (isAuthorized(command, enforcer)) {
            return publishMessageCommand(command, enforcer);
        } else {
            return CompletableFuture.failedStage(rejectMessageCommand(command));
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> publishMessageCommand(final MessageCommand<?, ?> command,
            final Enforcer enforcer) {

        final ResourceKey resourceKey =
                ResourceKey.newInstance(MessageCommand.RESOURCE_TYPE, command.getResourcePath());
        final EffectedSubjects effectedSubjects = enforcer.getSubjectsWithPermission(resourceKey, Permission.READ);
        final DittoHeaders headersWithReadSubjects = DittoHeaders.newBuilder(command.getDittoHeaders())
                .readGrantedSubjects(effectedSubjects.getGranted())
                .readRevokedSubjects(effectedSubjects.getRevoked())
                .build();

        final MessageCommand<?, ?> withReadSubjects = command.setDittoHeaders(headersWithReadSubjects);
        return publishLiveSignal(withReadSubjects, MESSAGE_COMMAND_ACK_EXTRACTOR, liveSignalPub.message());
    }

    private MessageSendNotAllowedException rejectMessageCommand(final MessageCommand<?, ?> command) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log(command).info(
                "The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationContext: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationContext());
        return error;
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>, S extends T> CompletionStage<Contextual<WithDittoHeaders>> publishLiveSignal(
            final S signal,
            final AckExtractor<S> ackExtractor,
            final DistributedPub<T> pub) {

        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        log(signal).debug("Publish message to pub-sub: <{}>", signal);
        return addToResponseReceiver(signal).thenApply(newSignal ->
                withMessageToReceiver(newSignal, pub.getPublisher(),
                        obj -> pub.wrapForPublicationWithAcks((S) obj, ackExtractor))
        );
    }

    private CompletionStage<Signal<?>> addToResponseReceiver(final Signal<?> signal) {
        final Optional<Cache<String, ActorRef>> cacheOptional = context.getResponseReceivers();
        if (cacheOptional.isPresent() && signal instanceof Command && signal.getDittoHeaders().isResponseRequired()) {
            return insertResponseReceiverConflictFree(cacheOptional.get(), signal, sender());
        } else {
            return CompletableFuture.completedStage(signal);
        }
    }

    private static boolean isAuthorized(final MessageCommand<?, ?> command, final Enforcer enforcer) {
        return enforcer.hasUnrestrictedPermissions(extractMessageResourceKey(command),
                command.getDittoHeaders().getAuthorizationContext(), WRITE);
    }

    private static ResourceKey extractMessageResourceKey(final MessageCommand<?, ?> command) {
        try {
            final JsonPointer resourcePath = command.getResourcePath();
            return PoliciesResourceType.messageResource(resourcePath);
        } catch (final IllegalArgumentException e) {
            throw MessageFormatInvalidException.newBuilder(JsonFactory.nullArray())
                    .message("Unable to determine message resource path.")
                    .description("Please verify that the thing ID, message subject and direction are set correctly.")
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    private static CompletionStage<Signal<?>> insertResponseReceiverConflictFree(final Cache<String, ActorRef> cache,
            final Signal<?> signal, final ActorRef responseReceiver) {

        return setUniqueCorrelationIdForGlobalDispatching(cache, signal)
                .thenApply(correlationIdAndSignal -> {
                    cache.put(correlationIdAndSignal.first(), responseReceiver);
                    return correlationIdAndSignal.second();
                });
    }

    private static CompletionStage<Pair<String, Signal<?>>> setUniqueCorrelationIdForGlobalDispatching(
            final Cache<String, ?> cache, final Signal<?> signal) {

        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final String correlationId = dittoHeaders.getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());

        return cache.get(correlationId).thenCompose(entry -> {
            final CompletionStage<String> uniqueCorrelationIdFuture;
            if (entry.isPresent()) {
                uniqueCorrelationIdFuture = findUniqueCorrelationId(cache, correlationId, getNextSuffix());
            } else {
                uniqueCorrelationIdFuture = CompletableFuture.completedStage(correlationId);
            }
            return uniqueCorrelationIdFuture.thenApply(newCorrelationId -> Pair.create(
                    newCorrelationId,
                    signal.setDittoHeaders(DittoHeaders.newBuilder(dittoHeaders)
                            // always set "keep-cid" to true because global dispatching is active
                            .correlationId(newCorrelationId)
                            .build())
            ));
        });
    }

    private static String getNextSuffix() {
        return Long.toHexString(Double.doubleToRawLongBits(Math.random()));
    }

    private static CompletionStage<String> findUniqueCorrelationId(final Cache<String, ?> cache,
            final String startingId, final String suffix) {

        final String nextCorrelationId = startingId + "#x" + suffix;
        return cache.get(nextCorrelationId).thenCompose(entry -> {
            if (entry.isPresent()) {
                return findUniqueCorrelationId(cache, startingId, getNextSuffix());
            } else {
                return CompletableFuture.completedStage(nextCorrelationId);
            }
        });
    }

}
