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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.EventSendNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcement<Signal> {

    private final EnforcerRetriever enforcerRetriever;
    private final Cache<String, ActorRef> responseReceivers;

    private LiveSignalEnforcement(final Contextual<Signal> context, final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {

        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);
        enforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
        responseReceivers = context.getResponseReceivers();
    }

    /**
     * {@link EnforcementProvider} for {@link LiveSignalEnforcement}.
     */
    public static final class Provider implements EnforcementProvider<Signal> {

        private final Cache<EntityId, Entry<EntityId>> thingIdCache;
        private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;
        private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;

        /**
         * Constructor.
         *
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         */
        public Provider(final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {

            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        }

        @Override
        public Class<Signal> getCommandClass() {
            return Signal.class;
        }

        @Override
        public boolean isApplicable(final Signal signal) {
            return LiveSignalEnforcement.isLiveSignal(signal);
        }

        @Override
        public AbstractEnforcement<Signal> createEnforcement(final Contextual<Signal> context) {
            return new LiveSignalEnforcement(context, thingIdCache, policyEnforcerCache, aclEnforcerCache);
        }

    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {

        final Signal liveSignal = signal();
        final ActorRef sender = sender();
        LogUtil.enhanceLogWithCorrelationIdOrRandom(liveSignal);
        return enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(liveSignal, sender, enforcerEntry)
                        .exceptionally(this::handleExceptionally);
            } catch (final RuntimeException e) {
                return CompletableFuture.completedFuture(handleExceptionally(e));
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final Signal liveSignal, final ActorRef sender,
            final Entry<Enforcer> enforcerEntry) {

        final Optional<String> correlationIdOpt = liveSignal.getDittoHeaders().getCorrelationId();
        if (enforcerEntry.exists() && correlationIdOpt.isPresent()) {
            final Enforcer enforcer = enforcerEntry.getValueOrThrow();
            final String correlationId = correlationIdOpt.get();

            if (liveSignal instanceof SendClaimMessage) {
                if (liveSignal.getDittoHeaders().isResponseRequired()) {
                    responseReceivers.put(correlationId, sender);
                }
                // claim messages require no enforcement, publish them right away:
                return CompletableFuture.completedFuture(
                        publishMessageCommand((SendClaimMessage) liveSignal, enforcer));

            } else if (liveSignal instanceof CommandResponse) {
                return enforceLiveCommandResponse(liveSignal, correlationId);
            } else if (liveSignal instanceof Command) {
                return enforceLiveCommand(liveSignal, sender, enforcer, correlationId);
            } else if (liveSignal instanceof ThingEvent) {
                return enforceLiveEvent(liveSignal, enforcer);
            } else {
                log().error("Unsupported Signal in LiveSignalEnforcement: <{}>", liveSignal);
                throw GatewayInternalErrorException.newBuilder()
                        .dittoHeaders(liveSignal.getDittoHeaders())
                        .build();
            }
        } else {
            // drop live command to nonexistent things and respond with error.
            log(liveSignal).info(
                    "Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                            " looked up! Answering with ThingNotAccessibleException.", liveSignal.getType(),
                    liveSignal.getId());
            throw ThingNotAccessibleException.newBuilder(entityId().getId())
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveCommandResponse(final Signal liveSignal,
            final String correlationId) {
        // no enforcement for responses required - the original sender will get the answer:
        return responseReceivers.get(correlationId)
                .thenApply(responseReceiver -> {
                    if (responseReceiver.isPresent()) {
                        responseReceivers.invalidate(correlationId);
                        log().debug("Scheduling CommandResponse <{}> to original sender: <{}>", liveSignal,
                                responseReceiver.get());
                        return withMessageToReceiver(liveSignal, responseReceiver.get());
                    } else {
                        log(liveSignal).warning("No outstanding responses receiver for CommandResponse <{}>",
                                liveSignal.getType());
                        return withoutReceiver();
                    }
                });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveCommand(final Signal liveSignal,
            final ActorRef sender, final Enforcer enforcer, final String correlationId) {
        // enforce both Live Commands and MessageCommands
        if (liveSignal instanceof MessageCommand) {

            final Contextual<WithDittoHeaders> contextual =
                    enforceMessageCommand((MessageCommand) liveSignal, enforcer);
            if (liveSignal.getDittoHeaders().isResponseRequired()) {
                responseReceivers.put(correlationId, sender);
            }
            return CompletableFuture.completedFuture(contextual);
        } else if (liveSignal instanceof ThingCommand) {
            // enforce Live Thing Commands
            final boolean authorized;
            if (enforcer instanceof AclEnforcer) {
                authorized = ThingCommandEnforcement.authorizeByAcl(enforcer, (ThingCommand<?>) liveSignal)
                        .isPresent();
            } else {
                authorized =
                        ThingCommandEnforcement.authorizeByPolicy(enforcer, (ThingCommand<?>) liveSignal)
                                .isPresent();
            }

            if (authorized) {
                final Command<?> withReadSubjects =
                        addReadSubjectsToThingSignal((Command<?>) liveSignal, enforcer);
                log(withReadSubjects).info("Live Command was authorized: <{}>", withReadSubjects);
                if (liveSignal.getDittoHeaders().isResponseRequired()) {
                    responseReceivers.put(correlationId, sender);
                }
                return CompletableFuture.completedFuture(
                        publishToMediator(withReadSubjects, StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic()));
            } else {
                log(liveSignal).info("Live Command was NOT authorized: <{}>", liveSignal);
                throw ThingCommandEnforcement.errorForThingCommand((ThingCommand) liveSignal);
            }
        } else {
            log(liveSignal).warning("Ignoring unsupported live command: <{}>", liveSignal);
            throw UnknownCommandException.newBuilder(liveSignal.getName())
                    .message("The sent command is not supported as live command")
                    .dittoHeaders(liveSignal.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<Contextual<WithDittoHeaders>> enforceLiveEvent(final Signal liveSignal,
            final Enforcer enforcer) {
        // enforce Live Events
        final boolean authorized = enforcer.hasUnrestrictedPermissions(
                // only check access to root resource for now
                PoliciesResourceType.thingResource("/"),
                liveSignal.getDittoHeaders().getAuthorizationContext(),
                WRITE);

        if (authorized) {
            log(liveSignal).info("Live Event was authorized: <{}>", liveSignal);
            final Event withReadSubjects = addReadSubjectsToThingSignal((Event<?>) liveSignal, enforcer);
            return CompletableFuture.completedFuture(
                    publishToMediator(withReadSubjects, StreamingType.LIVE_EVENTS.getDistributedPubSubTopic()));
        } else {
            log(liveSignal).info("Live Event was NOT authorized: <{}>", liveSignal);
            throw EventSendNotAllowedException.newBuilder(((ThingEvent) liveSignal).getThingId())
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
    static boolean isLiveSignal(final Signal signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    private Contextual<WithDittoHeaders> enforceMessageCommand(final MessageCommand command, final Enforcer enforcer) {
        if (isAuthorized(command, enforcer)) {
            return publishMessageCommand(command, enforcer);
        } else {
            throw rejectMessageCommand(command);
        }
    }

    private Contextual<WithDittoHeaders> publishMessageCommand(final MessageCommand command, final Enforcer enforcer) {

        final ResourceKey resourceKey =
                ResourceKey.newInstance(MessageCommand.RESOURCE_TYPE, command.getResourcePath());
        final Set<String> messageReaders = enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ)
                .getGranted();

        final DittoHeaders headersWithReadSubjects = command.getDittoHeaders()
                .toBuilder()
                .readSubjects(messageReaders)
                .build();

        final MessageCommand commandWithReadSubjects = command.setDittoHeaders(headersWithReadSubjects);

        // answer the sender immediately for fire-and-forget message commands.
        getResponseForFireAndForgetMessage(commandWithReadSubjects)
                .ifPresent(this::replyToSender);

        return publishToMediator(commandWithReadSubjects, commandWithReadSubjects.getTypePrefix());
    }

    /**
     * Reply a message to sender.
     *
     * @param message message to forward.
     */
    private void replyToSender(final Object message) {
        sender().tell(message, self());
    }

    private MessageSendNotAllowedException rejectMessageCommand(final MessageCommand command) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log(command).info(
                "The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationSubjects: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationSubjects());
        return error;
    }

    private Contextual<WithDittoHeaders> publishToMediator(final Signal<?> command, final String pubSubTopic) {
        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        log(command).debug("Publish message to pub-sub: <{}>", pubSubTopic);

        return withMessageToReceiver(command, pubSubMediator(), obj ->
                new DistributedPubSubMediator.Publish(pubSubTopic, obj));
    }

    private static boolean isAuthorized(final MessageCommand command, final Enforcer enforcer) {
        return enforcer.hasUnrestrictedPermissions(
                PoliciesResourceType.messageResource(command.getResourcePath()),
                command.getDittoHeaders().getAuthorizationContext(),
                WRITE);
    }

    /**
     * Creates an @{SendMessageAcceptedResponse} for a message command if it is fire-and-forget.
     *
     * @param command The message command.
     * @return The HTTP response if the message command is fire-and-forget, {@code Optional.empty()} otherwise.
     */
    private static Optional<SendMessageAcceptedResponse> getResponseForFireAndForgetMessage(
            final MessageCommand<?, ?> command) {
        if (isFireAndForgetMessage(command)) {
            return Optional.of(
                    SendMessageAcceptedResponse.newInstance(command.getThingId(), command.getMessage().getHeaders(),
                            command.getDittoHeaders()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Tests whether a message command is fire-and-forget.
     *
     * @param command The message command.
     * @return {@code true} if the message's timeout header is 0 or if the message is flagged not to require a response,
     * {@code false} otherwise.
     */
    private static boolean isFireAndForgetMessage(final MessageCommand<?, ?> command) {
        return command.getMessage()
                .getTimeout()
                .map(Duration::isZero)
                .orElseGet(() -> !command.getDittoHeaders().isResponseRequired());
    }

}
