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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.models.policies.Permission.WRITE;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.EffectedSubjects;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.messages.MessageFormatInvalidException;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.services.models.concierge.pubsub.LiveSignalPub;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.pubsub.DistributedPub;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.japi.Pair;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcement<Signal<?>> {

    private final EnforcerRetriever enforcerRetriever;
    private final LiveSignalPub liveSignalPub;

    private LiveSignalEnforcement(final Contextual<Signal<?>> context,
            final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
            final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache,
            final LiveSignalPub liveSignalPub) {

        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);
        enforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
        this.liveSignalPub = liveSignalPub;
    }

    /**
     * {@link EnforcementProvider} for {@link LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<Signal<?>> {

        private final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache;
        private final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache;
        private final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache;
        private final LiveSignalPub liveSignalPub;

        /**
         * Constructor.
         *
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         * @param liveSignalPub distributed-pub access for live signal publication
         */
        public Provider(final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
                final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityIdWithResourceType, Entry<Enforcer>> aclEnforcerCache,
                final LiveSignalPub liveSignalPub) {

            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
            this.liveSignalPub = liveSignalPub;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class<Signal<?>> getCommandClass() {
            return (Class) Signal.class;
        }

        @Override
        public boolean isApplicable(final Signal<?> signal) {
            return LiveSignalEnforcement.isLiveSignal(signal);
        }

        @Override
        public AbstractEnforcement<Signal<?>> createEnforcement(final Contextual<Signal<?>> context) {
            return new LiveSignalEnforcement(context, thingIdCache, policyEnforcerCache, aclEnforcerCache,
                    liveSignalPub);
        }

    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        final Signal<?> liveSignal = signal();
        LogUtil.enhanceLogWithCorrelationIdOrRandom(liveSignal);
        return enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(liveSignal, enforcerEntry)
                        .exceptionally(this::handleExceptionally);
            } catch (final RuntimeException e) {
                return CompletableFuture.completedFuture(handleExceptionally(e));
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final Signal<?> liveSignal,
            final Entry<Enforcer> enforcerEntry) {

        final Optional<String> correlationIdOpt = liveSignal.getDittoHeaders().getCorrelationId();
        if (enforcerEntry.exists() && correlationIdOpt.isPresent()) {
            final Enforcer enforcer = enforcerEntry.getValueOrThrow();

            if (liveSignal instanceof SendClaimMessage) {
                // claim messages require no enforcement, publish them right away:
                final SendClaimMessage<?> sendClaimMessage = (SendClaimMessage<?>) liveSignal;
                return publishMessageCommand(sendClaimMessage, enforcer);
            } else if (liveSignal instanceof CommandResponse) {
                return enforceLiveCommandResponse(liveSignal, correlationIdOpt.get());
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
            log(liveSignal).info(
                    "Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                            " looked up! Answering with ThingNotAccessibleException.", liveSignal.getType(),
                    liveSignal.getEntityId());
            throw ThingNotAccessibleException.newBuilder(ThingId.of(entityId().getId()))
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveCommandResponse(final Signal<?> liveSignal,
            final String correlationId) {

        final Optional<Cache<String, ActorRef>> responseReceiversOptional = context.getResponseReceivers();
        if (responseReceiversOptional.isPresent()) {
            final Cache<String, ActorRef> responseReceivers = responseReceiversOptional.get();
            return responseReceivers.get(correlationId).thenApply(responseReceiverEntry -> {
                if (responseReceiverEntry.isPresent()) {
                    responseReceivers.invalidate(correlationId);
                    final ActorRef responseReceiver = responseReceiverEntry.get();
                    log().debug("Scheduling CommandResponse <{}> to original sender <{}>", liveSignal,
                            responseReceiver);
                    return withMessageToReceiver(liveSignal, responseReceiverEntry.get());
                } else {
                    if (log().isDebugEnabled()) {
                        log().debug("Got <{}> with unknown correlation ID: <{}>", liveSignal.getType(), liveSignal);
                    } else {
                        log().info("Got <{}> with unknown correlation ID: <{}>", liveSignal.getType(), correlationId);
                    }
                    return withMessageToReceiver(null, null);
                }
            });
        } else {
            if (log().isDebugEnabled()) {
                log().debug("Got live response when global dispatching is inactive: <{}>", liveSignal);
            } else {
                log().info("Got live response when global dispatching is inactive: <{}> with correlation ID <{}>",
                        liveSignal.getType(),
                        liveSignal.getDittoHeaders().getCorrelationId().orElse(""));
            }
            return CompletableFuture.completedFuture(withMessageToReceiver(null, null));
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveSignal(final StreamingType streamingType,
            final Signal<?> liveSignal, final Enforcer enforcer) {

        switch (streamingType) {
            case MESSAGES:
                return enforceMessageCommand((MessageCommand<?, ?>) liveSignal, enforcer);
            case LIVE_EVENTS:
                return enforceLiveEvent(liveSignal, enforcer);
            case LIVE_COMMANDS:
                if (enforcer instanceof AclEnforcer) {
                    ThingCommandEnforcement.authorizeByAclOrThrow(enforcer, (ThingCommand<?>) liveSignal);
                } else {
                    ThingCommandEnforcement.authorizeByPolicyOrThrow(enforcer, (ThingCommand<?>) liveSignal);
                }
                final Command<?> withReadSubjects =
                        addEffectedReadSubjectsToThingSignal((Command<?>) liveSignal, enforcer);
                log(withReadSubjects).info("Live Command was authorized: <{}>", withReadSubjects);
                return publishLiveSignal(withReadSubjects, liveSignalPub.command());
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

        // enforce Live Events
        final boolean authorized = enforcer.hasUnrestrictedPermissions(
                // only check access to root resource for now
                PoliciesResourceType.thingResource("/"),
                liveSignal.getDittoHeaders().getAuthorizationContext(),
                WRITE);

        if (authorized) {
            log(liveSignal).info("Live Event was authorized: <{}>", liveSignal);
            final Event<?> withReadSubjects = addEffectedReadSubjectsToThingSignal((Event<?>) liveSignal, enforcer);
            return publishLiveSignal(withReadSubjects, liveSignalPub.event());
        } else {
            log(liveSignal).info("Live Event was NOT authorized: <{}>", liveSignal);
            throw EventSendNotAllowedException.newBuilder(((ThingEvent<?>) liveSignal).getThingEntityId())
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

        final MessageCommand<?, ?> commandWithReadSubjects = command.setDittoHeaders(headersWithReadSubjects);
        return publishLiveSignal(commandWithReadSubjects, liveSignalPub.message());
    }

    private MessageSendNotAllowedException rejectMessageCommand(final MessageCommand<?, ?> command) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getThingEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log(command).info(
                "The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationContext: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationContext());
        return error;
    }

    @SuppressWarnings("unchecked")
    private <T extends Signal<?>> CompletionStage<Contextual<WithDittoHeaders>> publishLiveSignal(final T signal,
            final DistributedPub<T> pub) {

        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        log(signal).debug("Publish message to pub-sub");
        return addToResponseReceiver(signal).thenApply(newSignal ->
                withMessageToReceiver(newSignal, pub.getPublisher(), obj -> pub.wrapForPublication((T) obj))
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
        return enforcer.hasUnrestrictedPermissions(
                extractMessageResourceKey(command),
                command.getDittoHeaders().getAuthorizationContext(),
                WRITE);
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
