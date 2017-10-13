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
package org.eclipse.ditto.signals.commands.live.modify;

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingCreated;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer LiveCommandAnswer} for a {@link
 * CreateThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class CreateThingLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<CreateThingLiveCommand, CreateThingLiveCommandAnswerBuilder.ResponseFactory, CreateThingLiveCommandAnswerBuilder.EventFactory>
        implements CreateThingLiveCommandAnswerBuilder {

    private CreateThingLiveCommandAnswerBuilderImpl(final CreateThingLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code CreateThingLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static CreateThingLiveCommandAnswerBuilderImpl newInstance(final CreateThingLiveCommand command) {
        return new CreateThingLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @Override
    protected Event doCreateEvent(final Function<EventFactory, Event<?>> createEventFunction) {
        return createEventFunction.apply(new EventFactoryImpl());
    }

    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public CreateThingResponse created() {
            return CreateThingResponse.of(command.getThing(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingConflictError() {
            final DittoRuntimeException exception = ThingConflictException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            return errorResponse(command.getThingId(), exception);
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public ThingCreated created() {
            return ThingCreated.of(command.getThing(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
