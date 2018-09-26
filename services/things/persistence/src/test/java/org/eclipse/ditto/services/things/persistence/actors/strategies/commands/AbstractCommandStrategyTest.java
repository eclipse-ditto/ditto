/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventHandleStrategy;
import org.eclipse.ditto.services.things.persistence.actors.strategies.events.EventStrategy;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
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
 * Abstract base implementation for unit tests of implementations of {@link AbstractCommandStrategy}.
 */
public abstract class AbstractCommandStrategyTest {

    protected static final long NEXT_REVISION = 42L;
    private static final EventStrategy<ThingEvent> eventHandleStrategy = EventHandleStrategy.getInstance();

    protected static final long THING_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(ThingCommandSizeValidator.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES, "-1"));

    protected static DiagnosticLoggingAdapter logger;
    protected static ThingSnapshotter thingSnapshotter;

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DiagnosticLoggingAdapter.class);
        thingSnapshotter = Mockito.mock(ThingSnapshotter.class);
    }

    protected static CommandStrategy.Context getDefaultContext() {
        final Runnable becomeCreatedRunnable = mock(Runnable.class);
        final Runnable becomeDeletedRunnable = mock(Runnable.class);
        return DefaultContext.getInstance(THING_ID, logger, thingSnapshotter, becomeCreatedRunnable,
                becomeDeletedRunnable);
    }

    protected static void assertModificationResult(final CommandStrategy underTest, @Nullable final Thing thing,
            final Command command, final Class<? extends ThingModifiedEvent> expectedEventClass,
            final CommandResponse expectedCommandResponse) {

        assertModificationResult(underTest, thing, command, expectedEventClass, expectedCommandResponse, false);
    }

    protected static void assertModificationResult(final CommandStrategy underTest, @Nullable final Thing thing,
            final Command command, final Class<? extends ThingModifiedEvent> expectedEventClass,
            final CommandResponse expectedCommandResponse, final boolean becomeDeleted) {

        final CommandStrategy.Context context = getDefaultContext();
        final CommandStrategy.Result result = applyStrategy(underTest, context, thing, command);

        assertModificationResult(context, result, expectedEventClass, thing, expectedCommandResponse, becomeDeleted);
    }

    protected static void assertErrorResult(final CommandStrategy underTest,
            @Nullable final Thing thing, final Command command,
            final DittoRuntimeException expectedException) {

        final CommandStrategy.Context context = getDefaultContext();
        final CommandStrategy.Result result = applyStrategy(underTest, context, thing, command);

        assertInfoResult(result, expectedException);
    }

    protected static void assertQueryResult(final CommandStrategy underTest,
            @Nullable final Thing thing, final Command command,
            final CommandResponse expectedCommandResponse) {

        final CommandStrategy.Context context = getDefaultContext();
        final CommandStrategy.Result result = applyStrategy(underTest, context, thing, command);

        assertInfoResult(result, expectedCommandResponse);
    }

    protected static void assertFutureResult(final CommandStrategy underTest,
            @Nullable final Thing thing, final Command command,
            final WithDittoHeaders expectedResponse) {

        final CommandStrategy.Context context = getDefaultContext();
        final CommandStrategy.Result result = applyStrategy(underTest, context, thing, command);

        assertInfoResult(context, result, expectedResponse, true);
    }

    protected static void assertUnhandledResult(final AbstractCommandStrategy underTest,
            @Nullable final Thing thing, final Command command,
            final WithDittoHeaders expectedResponse) {

        final CommandStrategy.Context context = getDefaultContext();
        @SuppressWarnings("unchecked")
        final CommandStrategy.Result result = underTest.unhandled(context, thing, NEXT_REVISION, command);

        assertInfoResult(result, expectedResponse);
    }

    private static void assertModificationResult(final CommandStrategy.Context context,
            final CommandStrategy.Result result,
            final Class<? extends ThingModifiedEvent> eventClazz, @Nullable final Thing currentThing,
            final WithDittoHeaders expectedResponse, final boolean becomeDeleted) {

        final ArgumentCaptor<ThingModifiedEvent> event = ArgumentCaptor.forClass(eventClazz);
        final DummyCommandHandler mock = mock(DummyCommandHandler.class);

        result.apply(context, mock::persist, mock::notify);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<BiConsumer<ThingModifiedEvent, Thing>> consumer = ArgumentCaptor.forClass(BiConsumer.class);

        verify(mock).persist(event.capture(), consumer.capture());
        assertThat(event.getValue()).isInstanceOf(eventClazz);
        final long newRevision = Optional.ofNullable(currentThing)
                .flatMap(Thing::getRevision)
                .map(rev -> rev.toLong() + 1)
                .orElse(0L);
        final Thing modifiedThing = eventHandleStrategy.handle(event.getValue(), currentThing, newRevision);
        consumer.getValue().accept(event.getValue(), modifiedThing);
        verify(mock).notify(expectedResponse);
        verify(context.getBecomeDeletedRunnable(), becomeDeleted ? times(1) : never()).run();
    }

    private static void assertInfoResult(final CommandStrategy.Result result, final WithDittoHeaders infoResponse) {
        final CommandStrategy.Context context = getDefaultContext();

        assertInfoResult(context, result, infoResponse, false);
    }

    private static void assertInfoResult(final CommandStrategy.Context context,
            final CommandStrategy.Result result,
            final WithDittoHeaders infoResponse, final boolean waitForNotification) {
        final DummyCommandHandler mock = mock(DummyCommandHandler.class);

        result.apply(context, mock::persist, mock::notify);

        if (waitForNotification) {
            verify(mock, timeout(3000)).notify(infoResponse);
        } else {
            verify(mock).notify(infoResponse);
        }
        verify(mock, never()).persist(any(), any());
        verify(context.getBecomeDeletedRunnable(), never()).run();
    }

    private static CommandStrategy.Result applyStrategy(final CommandStrategy underTest,
            final CommandStrategy.Context context,
            final @Nullable Thing thing,
            final Command command) {

        @SuppressWarnings("unchecked")
        final CommandStrategy.Result result = underTest.apply(context, thing, NEXT_REVISION, command);
        return result;
    }

    interface DummyCommandHandler {

        void persist(ThingModifiedEvent event, BiConsumer<ThingModifiedEvent, Thing> handler);

        void notify(WithDittoHeaders response);

        void becomeDeleted();

    }
}
