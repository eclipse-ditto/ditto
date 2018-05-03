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
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.messages.MessageSendNotAllowedException;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendMessageAcceptedResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;

/**
 * Authorize {@code MessageCommand}.
 */
public final class MessageCommandEnforcement extends AbstractEnforcement<MessageCommand> {

    private final EnforcerRetriever enforcerRetriever;

    private MessageCommandEnforcement(final Context context, final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {
        super(context);
        requireNonNull(thingIdCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);
        enforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
    }

    @Override
    public void enforce(final MessageCommand signal, final ActorRef sender) {
        enforcerRetriever.retrieve(entityId(), (idEntry, enforcerEntry) -> {
            if (idEntry.exists() && signal instanceof SendClaimMessage) {
                publishMessageCommand(signal, sender);
            } else if (enforcerEntry.exists()) {
                enforceMessageCommand(signal, enforcerEntry.getValue(), sender);
            } else {
                reportNonexistentEnforcer(signal, sender);
            }
        });
    }

    /**
     * {@link EnforcementProvider} for {@link MessageCommandEnforcement}.
     */
    public static final class Provider implements EnforcementProvider {

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
        public Provider(
                final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {
            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        }

        @Override
        public Class getCommandClass() {
            return MessageCommand.class;
        }

        @Override
        public AbstractEnforcement createEnforcement(final AbstractEnforcement.Context context) {
            return new MessageCommandEnforcement(context, thingIdCache, policyEnforcerCache, aclEnforcerCache);
        }
    }

    private void reportNonexistentEnforcer(final MessageCommand command, final ActorRef sender) {
        log().info("Command of type <{}> with ID <{}> could not be dispatched as no enforcer could be" +
                        " looked up! Answering with ThingNotAccessibleException.", command.getType(),
                command.getId());
        final Exception exception = ThingNotAccessibleException.newBuilder(command.getId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
        sender.tell(exception, ActorRef.noSender());
    }

    private void enforceMessageCommand(final MessageCommand command, final Enforcer enforcer, final ActorRef sender) {
        if (isAuthorized(command, enforcer)) {
            final DittoHeaders headersWithReadSubjects = command.getDittoHeaders()
                    .toBuilder()
                    .readSubjects(getThingsReadSubjects(command, enforcer))
                    .build();

            final MessageCommand commandWithReadSubjects = command.setDittoHeaders(headersWithReadSubjects);
            publishMessageCommand(commandWithReadSubjects, sender);
        } else {
            rejectMessageCommand(command, sender);
        }
    }

    private void rejectMessageCommand(final MessageCommand command, final ActorRef sender) {
        final MessageSendNotAllowedException error =
                MessageSendNotAllowedException.newBuilder(command.getThingId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();

        log().info("The command <{}> was not forwarded due to insufficient rights {}: {} - AuthorizationSubjects: {}",
                command.getType(), error.getClass().getSimpleName(), error.getMessage(),
                command.getDittoHeaders().getAuthorizationSubjects());
        replyToSender(error, sender);
    }

    private void publishMessageCommand(final MessageCommand command, final ActorRef sender) {
        publishToMediator(command, sender);

        // answer the sender immediately for fire-and-forget message commands.
        getResponseForFireAndForgetMessage(command)
                .ifPresent(response -> replyToSender(response, sender));
    }

    private void publishToMediator(final MessageCommand command, final ActorRef sender) {
        // using pub/sub to publish the command to any interested parties (e.g. a Websocket):
        final DistributedPubSubMediator.Publish publishMessage =
                new DistributedPubSubMediator.Publish(command.getTypePrefix(), command, true);
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
