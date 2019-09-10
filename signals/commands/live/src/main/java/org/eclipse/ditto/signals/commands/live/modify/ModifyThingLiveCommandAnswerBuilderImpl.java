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
package org.eclipse.ditto.signals.commands.live.modify;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingModified;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link ModifyThingLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyThingLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<ModifyThingLiveCommand, ModifyThingLiveCommandAnswerBuilder.ResponseFactory, ModifyThingLiveCommandAnswerBuilder.EventFactory>
        implements ModifyThingLiveCommandAnswerBuilder {

    private ModifyThingLiveCommandAnswerBuilderImpl(final ModifyThingLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyThingLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyThingLiveCommandAnswerBuilderImpl newInstance(final ModifyThingLiveCommand command) {
        checkNotNull(command, "command");
        return new ModifyThingLiveCommandAnswerBuilderImpl(command);
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
        public ModifyThingResponse created() {
            return ModifyThingResponse.created(command.getThing(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyThingResponse modified() {
            return ModifyThingResponse.modified(command.getThingEntityId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotAccessibleError() {
            return errorResponse(command.getThingEntityId(),
                    ThingNotAccessibleException.newBuilder(command.getThingEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse thingNotModifiableError() {
            return errorResponse(command.getThingEntityId(),
                    ThingNotModifiableException.newBuilder(command.getThingEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public ThingCreated created() {
            return ThingCreated.of(command.getThing(), -1, Instant.now(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingModified modified() {
            return ThingModified.of(command.getThing(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
