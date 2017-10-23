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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.text.MessageFormat;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Vector;

import akka.actor.AbstractActor;
import akka.actor.ActorCell;
import akka.actor.Terminated;
import akka.dispatch.DequeBasedMessageQueueSemantics;
import akka.dispatch.Envelope;
import scala.Option;

/**
 * Abstract actor with a custom stash that discards old messages if the stash capacity exceeds. The proper mailbox has
 * to be configured manually, and the mailbox should extend the {@link akka.dispatch.DequeBasedMessageQueueSemantics}
 * marker trait.
 */
abstract class AbstractActorWithDiscardOldStash extends AbstractActor {

    private final int capacity;
    private final ActorCell actorCell;
    private final Vector<Envelope> theStash;
    private final DequeBasedMessageQueueSemantics mailbox;

    AbstractActorWithDiscardOldStash() {
        actorCell = (ActorCell) getContext();
        if (!(actorCell.mailbox().messageQueue() instanceof DequeBasedMessageQueueSemantics)) {
            throw new IllegalArgumentException(MessageFormat
                    .format("DequeBasedMailbox required, got: {0}", actorCell.mailbox().getClass().getSimpleName()));
        }
        mailbox = (DequeBasedMessageQueueSemantics) actorCell.mailbox().messageQueue();
        capacity =
                getContext().system()
                        .mailboxes()
                        .stashCapacity(getContext().props().dispatcher(), getContext().props().mailbox());
        theStash = new Vector<>(capacity);
    }

    @Override
    public void preRestart(final Throwable reason, final Option<Object> message) throws Exception {
        try {
            unstashAll();
        } finally {
            super.preRestart(reason, message);
        }
    }

    @Override
    public void postStop() throws Exception {
        try {
            unstashAll();
        } finally {
            super.postStop();
        }
    }

    /**
     * Adds the current message (the message that the actor received last) to the actor's stash.
     *
     * @throws IllegalStateException if the same message is stashed more than once.
     */
    protected void stash() {
        final Envelope message = actorCell.currentMessage();
        if (!theStash.isEmpty() && (Objects.equals(message, theStash.lastElement()))) {
            throw new IllegalStateException("Can't stash the same message more than once: " + message.message());
        }
        if (capacity <= 0) {
            theStash.add(message);
        } else {
            while (theStash.size() > (capacity - 1)) {
                theStash.removeElementAt(0);
            }
            theStash.add(message);
        }
    }

    /**
     * Prepends all messages in the stash to the mailbox, and then clears the stash.
     * <p>
     * The stash is guaranteed to be empty after calling this method.
     */
    protected void unstashAll() {
        try {
            final ListIterator<Envelope> iterator = theStash.listIterator(theStash.size());
            while (iterator.hasPrevious()) {
                enqueueFirst(iterator.previous());
            }
        } finally {
            theStash.removeAllElements();
        }
    }

    /**
     * Enqueues {@code envelope} at the first position in the mailbox. If the message contained in
     * the envelope is a {@link Terminated} message, it will be ensured that it can be re-received
     * by the actor.
     *
     * @param envelope the envelope to enqueue.
     */
    private void enqueueFirst(final Envelope envelope) {
        mailbox.enqueueFirst(getSelf(), envelope);
        if (envelope.message() instanceof Terminated) {
            actorCell.terminatedQueuedFor(((Terminated) envelope.message()).getActor());
        }
    }

}
