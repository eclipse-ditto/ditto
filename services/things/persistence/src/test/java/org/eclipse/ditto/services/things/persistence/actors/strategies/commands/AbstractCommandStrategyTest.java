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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.junit.BeforeClass;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import akka.event.DiagnosticLoggingAdapter;

/**
 * Abstract base implementation for unit tests of implementations of {@link org.eclipse.ditto.services.utils.persistentactors.commands.AbstractCommandStrategy}.
 */
public abstract class AbstractCommandStrategyTest {

    protected static final long NEXT_REVISION = 42L;

    protected static final long THING_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(ThingCommandSizeValidator.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES, "-1"));

    protected static DiagnosticLoggingAdapter logger;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
    }

    protected static CommandStrategy.Context<ThingId> getDefaultContext() {
        return DefaultContext.getInstance(THING_ID, logger);
    }

    protected static void assertModificationResult(final CommandStrategy underTest,
            @Nullable final Thing thing,
            final Command command,
            final Class<? extends ThingModifiedEvent> expectedEventClass,
            final CommandResponse expectedCommandResponse) {

        assertModificationResult(underTest, thing, command, expectedEventClass, expectedCommandResponse, false);
    }

    protected static void assertModificationResult(final CommandStrategy underTest,
            @Nullable final Thing thing,
            final Command command,
            final Class<? extends ThingModifiedEvent> expectedEventClass,
            final CommandResponse expectedCommandResponse,
            final boolean becomeDeleted) {

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final Result result = applyStrategy(underTest, context, thing, command);

        assertModificationResult(context, result, expectedEventClass, expectedCommandResponse, becomeDeleted);
    }

    protected static void assertErrorResult(final CommandStrategy underTest,
            @Nullable final Thing thing,
            final Command command,
            final DittoRuntimeException expectedException) {

        final ResultVisitor<ThingEvent> mock = mock(Dummy.class);
        applyStrategy(underTest, getDefaultContext(), thing, command).accept(mock);
        verify(mock).onError(eq(expectedException));
    }

    protected static void assertQueryResult(final CommandStrategy underTest,
            @Nullable final Thing thing,
            final Command command,
            final CommandResponse expectedCommandResponse) {

        assertInfoResult(applyStrategy(underTest, getDefaultContext(), thing, command), expectedCommandResponse);
    }


    @SuppressWarnings("unchecked")
    protected static void assertUnhandledResult(final AbstractCommandStrategy underTest,
            @Nullable final Thing thing,
            final Command command,
            final DittoRuntimeException expectedResponse) {

        final ResultVisitor<ThingEvent> mock = mock(Dummy.class);
        underTest.unhandled(getDefaultContext(), thing, NEXT_REVISION, command).accept(mock);
        verify(mock).onError(expectedResponse);
    }

    private static void assertModificationResult(final CommandStrategy.Context<ThingId> context,
            final Result<ThingEvent> result,
            final Class<? extends ThingModifiedEvent> eventClazz,
            final WithDittoHeaders expectedResponse,
            final boolean becomeDeleted) {

        final ArgumentCaptor<ThingModifiedEvent> event = ArgumentCaptor.forClass(eventClazz);

        @SuppressWarnings("unchecked") final ResultVisitor<ThingEvent> mock = mock(ResultVisitor.class);

        result.accept(mock);

        verify(mock).onMutation(any(), event.capture(), eq(expectedResponse), anyBoolean(), eq(becomeDeleted));
        assertThat(event.getValue()).isInstanceOf(eventClazz);
    }

    private static void assertInfoResult(final Result<ThingEvent> result, final WithDittoHeaders infoResponse) {
        final ResultVisitor<ThingEvent> mock = mock(Dummy.class);
        result.accept(mock);
        verify(mock).onQuery(any(), eq(infoResponse));
    }

    @SuppressWarnings("unchecked")
    private static Result<ThingEvent> applyStrategy(final CommandStrategy underTest,
            final CommandStrategy.Context<ThingId> context,
            final @Nullable Thing thing,
            final Command command) {

        return underTest.apply(context, thing, NEXT_REVISION, command);
    }

    interface Dummy extends ResultVisitor<ThingEvent> {}

}
