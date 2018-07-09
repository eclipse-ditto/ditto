/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.Consumer;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableResult}.
 */
public class ImmutableResultTest {

    private final Dummy mock = mock(Dummy.class);
    private final ThingModifiedEvent thingModifiedEvent = mock(ThingModifiedEvent.class);
    private final WithDittoHeaders response = mock(WithDittoHeaders.class);
    private final DittoRuntimeException exception = mock(DittoRuntimeException.class);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableResult.class,
                areImmutable(),
                provided(ThingModifiedEvent.class, WithDittoHeaders.class,
                        DittoRuntimeException.class).areAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResult.class)
                .verify();
    }

    @Test
    public void testNotifyResponse() {
        ImmutableResult.of(response).apply(mock::persist, mock::notify, mock::becomeDeleted);
        verify(mock).notify(response);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void testNotifyException() {
        ImmutableResult.of(exception).apply(mock::persist, mock::notify, mock::becomeDeleted);
        verify(mock).notify(exception);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void testPersistAndNotify() {
        testPersistAndNotify(false);
    }

    @Test
    public void testPersistAndNotifyAndBecomeDeleted() {
        testPersistAndNotify(true);
    }

    private void testPersistAndNotify(final boolean becomeDeleted) {
        ImmutableResult.of(thingModifiedEvent, response, null, becomeDeleted)
                .apply(mock::persist, mock::notify, mock::becomeDeleted);
        final ArgumentCaptor<Consumer<ThingModifiedEvent>> consumer = ArgumentCaptor.forClass(Consumer.class);
        verify(mock).persist(same(thingModifiedEvent), consumer.capture());
        consumer.getValue().accept(thingModifiedEvent);
        verify(mock).notify(response);
        verify(mock, becomeDeleted ? times(1) : never()).becomeDeleted();
        verify(mock, never()).notify(exception);
    }

    interface Dummy {

        void persist(ThingModifiedEvent event, Consumer<ThingModifiedEvent> handler);

        void notify(WithDittoHeaders response);

        void becomeDeleted();
    }
}