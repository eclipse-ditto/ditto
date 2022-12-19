/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.DittoSystemProperties;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Abstract base implementation for unit tests of implementations of {@link org.eclipse.ditto.internal.utils.persistentactors.commands.AbstractCommandStrategy}.
 */
public abstract class AbstractCommandStrategyTest {

    protected static final long NEXT_REVISION = 42L;

    protected static final long THING_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(DittoSystemProperties.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES, "-1"));

    protected static DittoDiagnosticLoggingAdapter logger;

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    @BeforeClass
    public static void initTestConstants() {
        logger = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        Mockito.when(logger.withCorrelationId(Mockito.any(DittoHeaders.class))).thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class))).thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(CharSequence.class))).thenReturn(logger);
    }

    protected static CommandStrategy.Context<ThingId> getDefaultContext() {
        return DefaultContext.getInstance(THING_ID, logger, ACTOR_SYSTEM_RESOURCE.getActorSystem());
    }

    protected static <C extends Command<?>, T extends ThingModifiedEvent<?>> T assertModificationResult(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final Class<T> expectedEventClass,
            final CommandResponse<?> expectedCommandResponse) {

        return assertModificationResult(underTest, thing, command, expectedEventClass, expectedCommandResponse, false);
    }

    protected static <C extends Command<?>, T extends ThingModifiedEvent<?>> T assertModificationResult(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final Class<T> expectedEventClass,
            final CommandResponse<?> expectedCommandResponse,
            final boolean becomeDeleted) {

        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final Result<ThingEvent<?>> result = applyStrategy(underTest, context, thing, command);

        return assertModificationResult(result, expectedEventClass, expectedCommandResponse, becomeDeleted);
    }

    protected static <C extends Command<?>> void assertErrorResult(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final DittoRuntimeException expectedException) {

        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
        applyStrategy(underTest, getDefaultContext(), thing, command).accept(mock);
        verify(mock).onError(eq(expectedException), eq(command));
    }

    protected static <C extends Command<?>> void assertQueryResult(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final CommandResponse<?> expectedCommandResponse) {

        assertInfoResult(applyStrategy(underTest, getDefaultContext(), thing, command), expectedCommandResponse);
    }

    protected static <C extends Command<?>> void assertQueryResult(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final Consumer<CommandResponse<?>> commandResponseAssertions) {

        final Result<ThingEvent<?>> thingEventResult = applyStrategy(underTest, getDefaultContext(), thing, command);
        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
        thingEventResult.accept(mock);
        final ArgumentCaptor<CommandResponse<?>> captor = ArgumentCaptor.forClass(CommandResponse.class);
        verify(mock).onQuery(any(), captor.capture());
        commandResponseAssertions.accept(captor.getValue());
    }


    protected static <C extends Command<?>> void assertUnhandledResult(
            final AbstractCommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            @Nullable final Thing thing,
            final C command,
            final DittoRuntimeException expectedResponse) {

        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
        underTest.unhandled(getDefaultContext(), thing, NEXT_REVISION, command).accept(mock);
        verify(mock).onError(eq(expectedResponse), eq(command));
    }

    private static <T extends ThingModifiedEvent<?>> T assertModificationResult(final Result<ThingEvent<?>> result,
            final Class<T> eventClazz,
            final WithDittoHeaders expectedResponse,
            final boolean becomeDeleted) {

        final ArgumentCaptor<T> event = ArgumentCaptor.forClass(eventClazz);

        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);

        result.accept(mock);

        verify(mock).onMutation(any(), event.capture(), eq(expectedResponse), anyBoolean(), eq(becomeDeleted));
        assertThat(event.getValue()).isInstanceOf(eventClazz);
        return event.getValue();
    }

    private static void assertInfoResult(final Result<ThingEvent<?>> result, final WithDittoHeaders infoResponse) {
        final ResultVisitor<ThingEvent<?>> mock = mock(Dummy.class);
        result.accept(mock);
        verify(mock).onQuery(any(), eq(infoResponse));
    }

    private static <C extends Command<?>> Result<ThingEvent<?>> applyStrategy(
            final CommandStrategy<C, Thing, ThingId, ThingEvent<?>> underTest,
            final CommandStrategy.Context<ThingId> context,
            final @Nullable Thing thing,
            final C command) {

        return underTest.apply(context, thing, NEXT_REVISION, command);
    }

    interface Dummy extends ResultVisitor<ThingEvent<?>> {}

}
