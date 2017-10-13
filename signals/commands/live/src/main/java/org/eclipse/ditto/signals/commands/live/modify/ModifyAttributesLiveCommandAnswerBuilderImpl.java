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
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * ModifyAttributesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyAttributesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<ModifyAttributesLiveCommand, ModifyAttributesLiveCommandAnswerBuilder.ResponseFactory, ModifyAttributesLiveCommandAnswerBuilder.EventFactory>
        implements ModifyAttributesLiveCommandAnswerBuilder {

    private ModifyAttributesLiveCommandAnswerBuilderImpl(final ModifyAttributesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyAttributesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyAttributesLiveCommandAnswerBuilderImpl newInstance(final ModifyAttributesLiveCommand command) {
        return new ModifyAttributesLiveCommandAnswerBuilderImpl(command);
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
        public ModifyAttributesResponse created() {
            return ModifyAttributesResponse.created(command.getThingId(), command.getAttributes(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyAttributesResponse modified() {
            return ModifyAttributesResponse.modified(command.getThingId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributesNotAccessibleError() {
            return errorResponse(command.getThingId(), AttributesNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse attributesNotModifiableError() {
            return errorResponse(command.getThingId(), AttributesNotModifiableException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public AttributesCreated created() {
            return AttributesCreated.of(command.getThingId(), command.getAttributes(), -1, Instant.now(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public AttributesModified modified() {
            return AttributesModified.of(command.getThingId(), command.getAttributes(), -1, Instant.now(),
                    command.getDittoHeaders());
        }
    }

}
