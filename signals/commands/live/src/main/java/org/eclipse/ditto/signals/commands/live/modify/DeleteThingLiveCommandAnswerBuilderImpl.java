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

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotDeletableException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingDeleted;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link DeleteThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class DeleteThingLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<DeleteThingLiveCommand, DeleteThingLiveCommandAnswerBuilder.ResponseFactory, DeleteThingLiveCommandAnswerBuilder.EventFactory>
        implements DeleteThingLiveCommandAnswerBuilder {

    private DeleteThingLiveCommandAnswerBuilderImpl(final DeleteThingLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteThingLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static DeleteThingLiveCommandAnswerBuilderImpl newInstance(final DeleteThingLiveCommand command) {
        return new DeleteThingLiveCommandAnswerBuilderImpl(command);
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
        public DeleteThingResponse deleted() {
            return DeleteThingResponse.of(command.getThingId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotAccessibleError() {
            return errorResponse(command.getThingId(), ThingNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotDeletableError() {
            return errorResponse(command.getThingId(), ThingNotDeletableException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public ThingDeleted deleted() {
            return ThingDeleted.of(command.getThingId(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
