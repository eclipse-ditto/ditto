/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertyNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyCreated;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertyModified;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * ModifyFeatureDesiredPropertyLiveCommand}.
 *
 * @since 1.5.0
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<ModifyFeatureDesiredPropertyLiveCommand, ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder.EventFactory>
        implements ModifyFeatureDesiredPropertyLiveCommandAnswerBuilder {

    private ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl(final ModifyFeatureDesiredPropertyLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl newInstance(
            final ModifyFeatureDesiredPropertyLiveCommand command) {
        return new ModifyFeatureDesiredPropertyLiveCommandAnswerBuilderImpl(command);
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
        public ModifyFeatureDesiredPropertyResponse created() {
            return ModifyFeatureDesiredPropertyResponse.created(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredPropertyPointer(),
                    command.getDesiredPropertyValue(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyFeatureDesiredPropertyResponse modified() {
            return ModifyFeatureDesiredPropertyResponse.modified(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredPropertyPointer(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertyNotAccessibleError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertyNotAccessibleException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId(),
                            command.getDesiredPropertyPointer())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertyNotModifiableError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertyNotModifiableException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId(), command.getDesiredPropertyPointer())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeatureDesiredPropertyCreated created() {
            return FeatureDesiredPropertyCreated.of(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredPropertyPointer(),
                    command.getDesiredPropertyValue(), -1, Instant.now(), command.getDittoHeaders(), null);
        }

        @Nonnull
        @Override
        public FeatureDesiredPropertyModified modified() {
            return FeatureDesiredPropertyModified.of(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredPropertyPointer(), command.getDesiredPropertyValue(), -1, Instant.now(),
                    command.getDittoHeaders(), null);
        }
    }

}
