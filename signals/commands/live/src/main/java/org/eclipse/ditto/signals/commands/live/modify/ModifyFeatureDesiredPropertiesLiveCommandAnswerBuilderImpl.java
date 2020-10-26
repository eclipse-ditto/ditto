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
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesModified;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * ModifyFeatureDesiredPropertiesLiveCommand}.
 *
 * @since 1.5.0
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<ModifyFeatureDesiredPropertiesLiveCommand, ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder.EventFactory>
        implements ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder {

    private ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(final ModifyFeatureDesiredPropertiesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl newInstance(
            final ModifyFeatureDesiredPropertiesLiveCommand command) {
        return new ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilderImpl(command);
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
        public ModifyFeatureDesiredPropertiesResponse created() {
            return ModifyFeatureDesiredPropertiesResponse.created(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredProperties(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyFeatureDesiredPropertiesResponse modified() {
            return ModifyFeatureDesiredPropertiesResponse.modified(command.getThingEntityId(), command.getFeatureId(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertiesNotAccessibleError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertiesNotAccessibleException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featureDesiredPropertiesNotModifiableError() {
            return errorResponse(command.getThingEntityId(),
                    FeatureDesiredPropertiesNotModifiableException.newBuilder(command.getThingEntityId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeatureDesiredPropertiesCreated created() {
            return FeatureDesiredPropertiesCreated.of(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredProperties(), -1, Instant.now(), command.getDittoHeaders(), null);
        }

        @Nonnull
        @Override
        public FeatureDesiredPropertiesModified modified() {
            return FeatureDesiredPropertiesModified.of(command.getThingEntityId(), command.getFeatureId(),
                    command.getDesiredProperties(), -1, Instant.now(), command.getDittoHeaders(), null);
        }
    }

}
