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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingPreconditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link ThingConflictStrategy}.
 */
@SuppressWarnings({"rawtypes", "java:S3740"})
public final class ThingConflictStrategyTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingConflictStrategy.class, areImmutable());
    }

    @Test
    public void createConflictResultWithoutPrecondition() {
        final ThingConflictStrategy underTest = new ThingConflictStrategy();
        final ThingId thingId = ThingId.of("thing:id");
        final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setRevision(25L).build();
        final CommandStrategy.Context<ThingId> context = DefaultContext.getInstance(thingId,
                mockLoggingAdapter(), ACTOR_SYSTEM_RESOURCE.getActorSystem());
        final CreateThing command = CreateThing.of(thing, null, DittoHeaders.empty());
        final Result<ThingEvent<?>> result = underTest.apply(context, thing, 26L, command);
        result.accept(new ExpectErrorVisitor(ThingConflictException.class));
    }

    @Test
    public void createPreconditionFailedResultWithPrecondition() {
        final ThingConflictStrategy underTest = new ThingConflictStrategy();
        final ThingId thingId = ThingId.of("thing:id");
        final Thing thing = ThingsModelFactory.newThingBuilder().setId(thingId).setRevision(25L).build();
        final CommandStrategy.Context<ThingId> context = DefaultContext.getInstance(thingId,
                mockLoggingAdapter(), ACTOR_SYSTEM_RESOURCE.getActorSystem());
        final CreateThing command = CreateThing.of(thing, null, DittoHeaders.newBuilder()
                .ifNoneMatch(EntityTagMatchers.fromStrings("*"))
                .build());
        final Result<ThingEvent<?>> result = underTest.apply(context, thing, 26L, command);
        result.accept(new ExpectErrorVisitor(ThingPreconditionFailedException.class));
    }

    private static DittoDiagnosticLoggingAdapter mockLoggingAdapter() {
        final DittoDiagnosticLoggingAdapter mock = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        doAnswer(invocation -> mock).when(mock).withCorrelationId(any(WithDittoHeaders.class));
        return mock;
    }

    private static final class ExpectErrorVisitor implements ResultVisitor<ThingEvent<?>> {

        private final Class<? extends DittoRuntimeException> clazz;

        private ExpectErrorVisitor(final Class<? extends DittoRuntimeException> clazz) {
            this.clazz = clazz;
        }

        @Override
        public void onMutation(final Command<?> command, final ThingEvent<?> event, final WithDittoHeaders response,
                final boolean becomeCreated, final boolean becomeDeleted) {
            throw new AssertionError("Expect error, got mutation: " + event);
        }

        @Override
        public void onQuery(final Command<?> command, final WithDittoHeaders response) {
            throw new AssertionError("Expect error, got query: " + response);
        }

        @Override
        public void onError(final DittoRuntimeException error, final Command errorCausingCommand) {
            assertThat(error).isInstanceOf(clazz);
        }
    }

}
