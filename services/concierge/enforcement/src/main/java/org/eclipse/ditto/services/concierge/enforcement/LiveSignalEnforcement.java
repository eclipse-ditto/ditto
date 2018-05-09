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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.models.policies.Permission.WRITE;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;

/**
 * Enforces live commands (including message commands) and live events.
 */
public final class LiveSignalEnforcement extends AbstractEnforcement<Signal> {

    private final EnforcerRetriever enforcerRetriever;

    private final Map<String, ActorRef> outstandingCommands;

    private LiveSignalEnforcement(final Context context, final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {

        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);
        enforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
        outstandingCommands = new HashMap<>();
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
        public AbstractEnforcement<Signal> createEnforcement(final Context context) {
            return new LiveSignalEnforcement(context, thingIdCache, policyEnforcerCache, aclEnforcerCache);
        }
    }

    @Override
    public void enforce(final Signal signal, final ActorRef sender) {
        enforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            if (enforcerEntry.exists()) {
                final Enforcer enforcer = enforcerEntry.getValue();

                final String correlationId = signal.getDittoHeaders().getCorrelationId().get();
                if (signal instanceof SendClaimMessage) {
                    // claim messages require no enforcement, publish them right away:
                    publishMessageCommand((SendClaimMessage) signal, enforcer, sender);
                    if (signal.getDittoHeaders().isResponseRequired()) {
                        outstandingCommands.put(correlationId, sender);
                    }
                } else if (signal instanceof CommandResponse) {
                    // no enforcement for responses required - the original sender will get the answer:
                    final ActorRef responseReceiver = outstandingCommands.remove(correlationId);
                    if (responseReceiver != null) {
                        responseReceiver.tell(signal, sender);
                    } else {
                        log().warning("No outstanding responses receiver for CommandResponse <{}>", signal.getType());
                    }
                } else if (signal instanceof Command) {
                    // enforce both Live Commands and MessageCommands
                    if (signal instanceof MessageCommand) {

                        final boolean wasPublished = enforceMessageCommand((MessageCommand) signal, enforcer, sender);
                        if (wasPublished && signal.getDittoHeaders().isResponseRequired()) {
                            outstandingCommands.put(correlationId, sender);
                        }
                    } else if (signal instanceof ThingCommand) {
                        // enforce Live Thing Commands
                        // TODO TJ must work with both ACL + Policies -> reuse ThingCommandEnforcement ??
                        final boolean authorized;
                        if (enforcer instanceof AclEnforcer) {
                            authorized = ThingCommandEnforcement.authorizeByAcl(enforcer, (ThingCommand<?>) signal)
                                    .isPresent();
                        } else {
                            authorized = ThingCommandEnforcement.authorizeByPolicy(enforcer, (ThingCommand<?>) signal)
                                    .isPresent();
                        }

                        if (authorized) {
                            log().info("Live Signal was authorized: <{}>", signal);
                            final Command<?> withReadSubjects = addReadSubjectsToThingSignal((Command<?>) signal, enforcer);
                            publishToMediator(withReadSubjects, "things-live-commands", sender);
                            // TODO TJ common constant
                            if (signal.getDittoHeaders().isResponseRequired()) {
                                outstandingCommands.put(correlationId, sender);
                            }
                        }
                    } else {
                        log().error("Ignoring unsupported live signal: <{}>", signal);
                    }

                } else if (signal instanceof Event) {
                    // TODO TJ enforce Live Events
                    // TODO TJ look on master how this is solved there -> probably WRITE required
                    log().warning("currently not correctly handled live event: <{}>", signal);
                    final Event<?> withReadSubjects = addReadSubjectsToThingSignal((Event<?>) signal, enforcer);
                    publishToMediator(withReadSubjects, "things-live-events", sender);
                    // TODO TJ common constant
                }
            } else {
                // drop live command to nonexistent things and respond with error.
                log().info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                                " looked up! Answering with ThingNotAccessibleException.", signal.getType(),
                        signal.getId());
                final ThingNotAccessibleException error = ThingNotAccessibleException.newBuilder(entityId().getId())
                        .dittoHeaders(signal.getDittoHeaders())
                        .build();
                replyToSender(error, sender);
            }
        });
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

    private boolean enforceMessageCommand(final MessageCommand command, final Enforcer enforcer, final ActorRef sender) {
        if (isAuthorized(command, enforcer)) {
            publishMessageCommand(command, enforcer, sender);
            return true;
        } else {
            rejectCommand(command, sender);
            return false;
        }
    }

    private void publishMessageCommand(final MessageCommand command, final Enforcer enforcer, final ActorRef sender) {
        final DittoHeaders headersWithReadSubjects = command.getDittoHeaders()
                .toBuilder()
                .readSubjects(getThingsReadSubjects(command, enforcer)) // TODO TJ we should add "message" READ subjects here, or not?
                .build();

        final MessageCommand commandWithReadSubjects = command.setDittoHeaders(headersWithReadSubjects);

        publishToMediator(commandWithReadSubjects, commandWithReadSubjects.getTypePrefix(), sender);

        // answer the sender immediately for fire-and-forget message commands.
        getResponseForFireAndForgetMessage(commandWithReadSubjects)
                .ifPresent(response -> replyToSender(response, sender));
    }

    private void rejectCommand(final MessageCommand command, final ActorRef sender) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log().info("The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationSubjects: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationSubjects());
        replyToSender(error, sender);
    }

    private void publishToMediator(final Signal<?> command, final String pubSubTopic, final ActorRef sender) {
        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        log().debug("Publish message to pub-sub: <{}>", pubSubTopic);

        final DistributedPubSubMediator.Publish publishMessage =
                new DistributedPubSubMediator.Publish(pubSubTopic, command, true);
        pubSubMediator().tell(publishMessage, sender);
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
