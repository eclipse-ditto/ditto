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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyTooManyModifyingRequestsException;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommand;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.DequeBasedMessageQueue;
import akka.dispatch.Envelope;
import akka.dispatch.MailboxType;
import akka.dispatch.MessageQueue;
import akka.dispatch.ProducesMessageQueue;
import akka.dispatch.UnboundedDequeBasedMessageQueueSemantics;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * Mailbox which handles {@link PolicyModifyCommand}s which should be passed to the {@link PolicyPersistenceActor} in a
 * special way: those are not enqueued into the mailbox if the mailbox size gets bigger than the configured
 * {@link #capacity}.
 * <p>
 * In that case the {@link PolicyPersistenceActor} cannot write the modifications fast enough to the persistence
 * and it makes no sense to enqueue further modifications.
 * </p>
 * <p>
 * {@link org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommand}s and other messages (e.g. Recovery-Messages) are
 * always enqueued and not treated in a special way.
 * </p>
 */
public class PolicyPersistenceActorMailbox implements MailboxType,
        ProducesMessageQueue<PolicyPersistenceActorMailbox.PolicyPersistenceActorMessageQueue> {

    private final int capacity;

    /**
     * Creates a new {@code PolicyBoundedMailbox}.
     * This constructor signature must exist, it will be called by Akka.
     *
     * @param settings the ActorSystem settings.
     * @param config the config.
     */
    public PolicyPersistenceActorMailbox(final ActorSystem.Settings settings, final Config config) {
        // put your initialization code here
        capacity = config.getInt("mailbox-capacity");
        if (capacity < 1) {
            throw new IllegalArgumentException("Mailbox capacity must not be less than 1");
        }
    }

    @Override
    public MessageQueue create(final scala.Option<ActorRef> owner, final scala.Option<ActorSystem> system) {
        // The create method is called to create the MessageQueue
        return new PolicyPersistenceActorMessageQueue(capacity, system.get());
    }

    /**
     * The MessageQueue implementation.
     */
    static class PolicyPersistenceActorMessageQueue extends LinkedBlockingDeque<Envelope> implements
            DequeBasedMessageQueue, UnboundedDequeBasedMessageQueueSemantics {

        private static final long serialVersionUID = -3799029649510677683L;

        private final transient LoggingAdapter log;

        private final int capacity;

        PolicyPersistenceActorMessageQueue(final int capacity, final ActorSystem actorSystem) {
            log = Logging.getLogger(actorSystem, PolicyPersistenceActorMessageQueue.class);
            this.capacity = capacity;
        }

        @Override
        public Deque<Envelope> queue() {
            return this;
        }

        @Override
        public void enqueue(final ActorRef receiver, final Envelope handle) {
            final Object message = handle.message();
            if (message instanceof PolicyModifyCommand) {
                queueSizeBasedAction(handle.sender(), (PolicyModifyCommand) message, () -> queue().add(handle));
            } else {
                // all other messages are enqueued right away and with no "limit":
                queue().add(handle);
            }
        }

        @Override
        public void enqueueFirst(final ActorRef receiver, final Envelope handle) {
            final Object message = handle.message();
            if (message instanceof PolicyModifyCommand) {
                queueSizeBasedAction(handle.sender(), (PolicyModifyCommand) message, () -> queue().addFirst(handle));
            } else {
                // all other messages are enqueued right away and with no "limit":
                queue().addFirst(handle);
            }
        }

        private void queueSizeBasedAction(final ActorRef sender, final PolicyModifyCommand command, final Runnable r) {
            // instead of blocking return "too many requests" response if numberOfMessages > capacity
            if (numberOfMessages() > capacity) {
                log.warning("Number of messages ({}) in the Mailbox of thing with ID '{}' exceeded the max capacity of "
                                + "{} -> rejecting PolicyModifyCommand '{}'", numberOfMessages(), command.getId(), capacity,
                        command.getType());

                final PolicyErrorResponse errorResponse =
                        PolicyErrorResponse.of(command.getId(), PolicyTooManyModifyingRequestsException //
                                .newBuilder(command.getId()) //
                                .dittoHeaders(command.getDittoHeaders()) //
                                .build());
                sender.tell(errorResponse, null);
            } else {
                r.run();
            }
        }

        @Override
        public Envelope dequeue() {
            return queue().poll();
        }

        @Override
        public int numberOfMessages() {
            return queue().size();
        }

        @Override
        public boolean hasMessages() {
            return !queue().isEmpty();
        }

        @Override
        public void cleanUp(final ActorRef owner, final MessageQueue deadLetters) {
            if (hasMessages()) {
                Envelope envelope = dequeue();
                while (envelope != null) {
                    deadLetters.enqueue(owner, envelope);
                    envelope = dequeue();
                }
            }
        }
    }
}
