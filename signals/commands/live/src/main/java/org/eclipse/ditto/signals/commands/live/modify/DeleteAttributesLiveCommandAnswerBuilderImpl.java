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
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * DeleteAttributesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class DeleteAttributesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<DeleteAttributesLiveCommand, DeleteAttributesLiveCommandAnswerBuilder.ResponseFactory, DeleteAttributesLiveCommandAnswerBuilder.EventFactory>
        implements DeleteAttributesLiveCommandAnswerBuilder {

    private DeleteAttributesLiveCommandAnswerBuilderImpl(final DeleteAttributesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteAttributesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static DeleteAttributesLiveCommandAnswerBuilderImpl newInstance(final DeleteAttributesLiveCommand command) {
        return new DeleteAttributesLiveCommandAnswerBuilderImpl(command);
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
        public DeleteAttributesResponse deleted() {
            return DeleteAttributesResponse.of(command.getThingId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributesNotAccessibleError() {
            final DittoRuntimeException exception = AttributesNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            return errorResponse(command.getThingId(), exception);
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributesNotModifiableError() {
            final DittoRuntimeException exception = AttributesNotModifiableException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            return errorResponse(command.getThingId(), exception);
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public AttributesDeleted deleted() {
            return AttributesDeleted.of(command.getThingId(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
