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

import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingTooManyModifyingRequestsException;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

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
 * Mailbox which handles {@link ThingModifyCommand}s which should be passed to the {@link ThingPersistenceActor} in a
 * special way: those are not enqueued into the mailbox if the mailbox size gets bigger than the configured {@link
 * #capacity}. <p> In that case the {@link ThingPersistenceActor} cannot write the modifications fast enough to the
 * persistence and it makes no sense to enqueue further modifications. </p> <p> {@link org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand}s and other
 * messages (e.g. Recovery-Messages) are always enqueued and not treated in a special way. </p>
 */
public class ThingPersistenceActorMailbox implements MailboxType,
        ProducesMessageQueue<ThingPersistenceActorMailbox.ThingPersistenceActorMessageQueue> {

    private final int capacity;

    /**
     * Creates a new {@code ThingBoundedMailbox}. This constructor signature must exist, it will be called by Akka.
     *
     * @param settings the ActorSystem settings.
     * @param config the config.
     */
    public ThingPersistenceActorMailbox(final ActorSystem.Settings settings, final Config config) {
        // put your initialization code here
        capacity = config.getInt("mailbox-capacity");
        if (capacity < 1) {
            throw new IllegalArgumentException("Mailbox capacity must not be less than 1");
        }
    }

    @Override
    public MessageQueue create(final scala.Option<ActorRef> owner, final scala.Option<ActorSystem> system) {
        // The create method is called to create the MessageQueue
        return new ThingPersistenceActorMessageQueue(capacity, system.get());
    }

    /**
     * The MessageQueue implementation.
     */
    public static class ThingPersistenceActorMessageQueue extends LinkedBlockingDeque<Envelope> implements
            DequeBasedMessageQueue, UnboundedDequeBasedMessageQueueSemantics {

        private static final long serialVersionUID = -3799029649510677683L;

        private final transient LoggingAdapter log;

        private final int capacity;

        public ThingPersistenceActorMessageQueue(final int capacity, final ActorSystem actorSystem) {
            log = Logging.getLogger(actorSystem, ThingPersistenceActorMessageQueue.class);
            this.capacity = capacity;
        }

        @Override
        public Deque<Envelope> queue() {
            return this;
        }

        @Override
        public void enqueue(final ActorRef receiver, final Envelope handle) {
            final Object message = handle.message();
            if (message instanceof ThingModifyCommand) {
                queueSizeBasedAction(handle.sender(), (ThingModifyCommand<?>) message, () -> queue().add(handle));
            } else {
                // all other messages are enqueued right away and with no "limit":
                queue().add(handle);
            }
        }

        @Override
        public void enqueueFirst(final ActorRef receiver, final Envelope handle) {
            final Object message = handle.message();
            if (message instanceof ThingModifyCommand) {
                queueSizeBasedAction(handle.sender(), (ThingModifyCommand<?>) message, () -> queue().addFirst(handle));
            } else {
                // all other messages are enqueued right away and with no "limit":
                queue().addFirst(handle);
            }
        }

        private void queueSizeBasedAction(final ActorRef sender, final ThingModifyCommand<?> command, final Runnable r) {
            // instead of blocking return "too many requests" response if numberOfMessages > capacity
            if (numberOfMessages() > capacity) {
                log.warning("Number of messages ({}) in the Mailbox of thing with ID '{}' exceeded the max capacity of "
                                + "{} -> rejecting ThingModifyCommand '{}'", numberOfMessages(), command.getEntityId(), capacity,
                        command.getType());

                final ThingErrorResponse errorResponse = ThingErrorResponse.of(command.getEntityId(),
                        ThingTooManyModifyingRequestsException //
                                .newBuilder(command.getEntityId()) //
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
