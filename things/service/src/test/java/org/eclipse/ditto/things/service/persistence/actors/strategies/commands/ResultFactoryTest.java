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

package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.Test;

/**
 * Unit tests for {@link org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory}.
 */
public final class ResultFactoryTest {

    private final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
    private final ThingQueryCommand thingQueryCommand = mock(ThingQueryCommand.class);
    private final ThingModifyCommand thingModifyCommand = mock(ThingModifyCommand.class);
    private final ThingModifiedEvent thingModifiedEvent = mock(ThingModifiedEvent.class);
    private final ThingCommandResponse response = mock(ThingCommandResponse.class);
    private final DittoRuntimeException exception = mock(DittoRuntimeException.class);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ResultFactory.class, areImmutable());
    }

    @Test
    public void notifyQueryResponse() {
        final Result<ThingEvent<?>> result = ResultFactory.newQueryResult(thingQueryCommand, response);
        result.accept(mock);
        verify(mock).onQuery(eq(thingQueryCommand), eq(response));
    }

    @Test
    public void notifyException() {
        final Command command = mock(Command.class);
        final Result<ThingEvent<?>> result = ResultFactory.newErrorResult(exception, command);
        result.accept(this.mock);
        verify(this.mock).onError(eq(exception), eq(command));
    }

    @Test
    public void notifyMutationResponseWithNeitherBecomingDeletedNorCreated() {
        assertNotifyMutationResponse(false, false);
    }

    @Test
    public void notifyMutationResponseWithBecomingCreated() {
        assertNotifyMutationResponse(true, false);
    }

    @Test
    public void notifyMutationResponseWithBecomingDeleted() {
        assertNotifyMutationResponse(false, true);
    }

    private void assertNotifyMutationResponse(final boolean becomeCreated, final boolean becomeDeleted) {
        final Result<ThingEvent<?>> result =
                ResultFactory.newMutationResult(thingModifyCommand, thingModifiedEvent, response,
                        becomeCreated,
                        becomeDeleted);
        result.accept(mock);
        verify(mock).onMutation(eq(thingModifyCommand), eq(thingModifiedEvent), eq(response), eq(becomeCreated),
                eq(becomeDeleted));
    }

    interface Dummy extends ResultVisitor<ThingEvent<?>> {
    }

}
