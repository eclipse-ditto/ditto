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
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ResultFactory}.
 */
public final class ResultFactoryTest {

    private final Dummy mock = mock(Dummy.class);
    private final ThingModifiedEvent thingModifiedEvent = mock(ThingModifiedEvent.class);
    private final ThingCommandResponse response = mock(ThingCommandResponse.class);
    private final DittoRuntimeException exception = mock(DittoRuntimeException.class);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ResultFactory.class, areImmutable());
    }

    @Test
    public void notifyResponse() {
        final CommandStrategy.Result result = ResultFactory.newResult(response);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        verify(mock).notify(response);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void notifyException() {
        final CommandStrategy.Result result = ResultFactory.newResult(exception);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        verify(mock).notify(exception);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void persistAndNotify() {
        persistAndNotify(false);
    }

    @Test
    public void persistAndNotifyAndBecomeDeleted() {
        persistAndNotify(true);
    }

    private void persistAndNotify(final boolean becomeDeleted) {
        final CommandStrategy.Result result = ResultFactory.newResult(thingModifiedEvent, response, becomeDeleted);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        @SuppressWarnings("unchecked")
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