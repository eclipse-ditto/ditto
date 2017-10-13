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
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * DeleteAttributeLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class DeleteAttributeLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<DeleteAttributeLiveCommand, DeleteAttributeLiveCommandAnswerBuilder.ResponseFactory, DeleteAttributeLiveCommandAnswerBuilder.EventFactory>
        implements DeleteAttributeLiveCommandAnswerBuilder {

    private DeleteAttributeLiveCommandAnswerBuilderImpl(final DeleteAttributeLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code DeleteAttributeLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static DeleteAttributeLiveCommandAnswerBuilderImpl newInstance(final DeleteAttributeLiveCommand command) {
        return new DeleteAttributeLiveCommandAnswerBuilderImpl(command);
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
        public DeleteAttributeResponse deleted() {
            return DeleteAttributeResponse.of(command.getThingId(), command.getAttributePointer(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributeNotAccessibleError() {
            final DittoRuntimeException exception = AttributeNotAccessibleException.newBuilder(command.getThingId(),
                    command.getAttributePointer())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            return errorResponse(command.getThingId(), exception);
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributeNotModifiableError() {
            final DittoRuntimeException exception = AttributeNotModifiableException.newBuilder(command.getThingId(),
                    command.getAttributePointer())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
            return errorResponse(command.getThingId(), exception);
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public AttributeDeleted deleted() {
            return AttributeDeleted.of(command.getThingId(), command.getAttributePointer(), -1, Instant.now(),
                    command.getDittoHeaders());
        }
    }

}
