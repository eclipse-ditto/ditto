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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.BiConsumer;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ResultFactory}.
 */
public final class ResultFactoryTest {

    private final Dummy mock = mock(Dummy.class);
    private final Thing thing = mock(Thing.class);
    private final ThingQueryCommand thingQueryCommand = mock(ThingQueryCommand.class);
    private final ThingModifyCommand thingModifyCommand = mock(ThingModifyCommand.class);
    private final ThingModifiedEvent thingModifiedEvent = mock(ThingModifiedEvent.class);
    private final ThingCommandResponse response = mock(ThingCommandResponse.class);
    private final ETagEntityProvider eTagEntityProvider = mock(ETagEntityProvider.class);
    private final DittoRuntimeException exception = mock(DittoRuntimeException.class);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ResultFactory.class, areImmutable());
    }

    @Test
    public void notifyQueryResponse() {
        final CommandStrategy.Result result =
                ResultFactory.newQueryResult(thingQueryCommand, thing, response, eTagEntityProvider);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        verify(mock).notify(response);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void notifyException() {
        final CommandStrategy.Result result = ResultFactory.newErrorResult(exception);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        verify(mock).notify(exception);
        verify(mock, never()).persist(any(), any());
        verify(mock, never()).becomeDeleted();
    }

    @Test
    public void notifyMutationResponseWithNotBecomingDeleted() {
        assertNotifyMutationResponse(false);
    }

    @Test
    public void notifyMutationResponseWithBecomingDeleted() {
        assertNotifyMutationResponse(true);
    }

    private void assertNotifyMutationResponse(final boolean becomeDeleted) {
        final CommandStrategy.Result result =
                ResultFactory.newMutationResult(thingModifyCommand, thingModifiedEvent, response, becomeDeleted,
                        eTagEntityProvider);

        result.apply(mock::persist, mock::notify, mock::becomeDeleted);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<BiConsumer<ThingModifiedEvent, Thing>> consumer = ArgumentCaptor.forClass(BiConsumer.class);
        verify(mock).persist(same(thingModifiedEvent), consumer.capture());
        consumer.getValue().accept(thingModifiedEvent, thing);
        verify(mock).notify(response);
        verify(mock, becomeDeleted ? times(1) : never()).becomeDeleted();
        verify(mock, never()).notify(exception);
    }

    interface Dummy {

        void persist(ThingModifiedEvent event, BiConsumer<ThingModifiedEvent, Thing> handler);

        void notify(WithDittoHeaders response);

        void becomeDeleted();

    }

}