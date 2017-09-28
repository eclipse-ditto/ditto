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
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorRef;

/**
 * Contains contextual fields for an EnforcerLookup.
 */
@Immutable
public final class LookupContext<T extends Signal<T>> {

    private final Signal<T> initialCommandOrEvent;
    private final ActorRef initialSender;
    private final ActorRef lookupRecipient;

    private LookupContext(final Signal<T> theInitialCommandOrEvent, final ActorRef theInitialSender,
            final ActorRef theLookupRecipient) {
        
        initialCommandOrEvent = checkNotNull(theInitialCommandOrEvent, "initial command or event");
        initialSender = checkNotNull(theInitialSender, "initial sender");
        lookupRecipient = checkNotNull(theLookupRecipient, "lookup recipient");
    }

    /**
     * Returns an instance of {@code LookupContext}.
     * 
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T extends Signal<T>> LookupContext<T> getInstance(final Signal<T> initialCommandOrEvent,
            final ActorRef initialSender, final ActorRef lookupRecipient) {

        return new LookupContext<>(initialCommandOrEvent, initialSender, lookupRecipient);
    }

    public Signal<T> getInitialCommandOrEvent() {
        return initialCommandOrEvent;
    }

    public ActorRef getInitialSender() {
        return initialSender;
    }

    public ActorRef getLookupRecipient() {
        return lookupRecipient;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LookupContext that = (LookupContext) o;
        return Objects.equals(initialCommandOrEvent, that.initialCommandOrEvent) &&
                Objects.equals(initialSender, that.initialSender) &&
                Objects.equals(lookupRecipient, that.lookupRecipient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialCommandOrEvent, initialSender, lookupRecipient);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "initialCommandOrEvent=" + initialCommandOrEvent +
                ", initialSender=" + initialSender +
                ", lookupRecipient=" + lookupRecipient +
                "]";
    }

}
