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
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * ModifyFeaturePropertiesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<ModifyFeaturePropertiesLiveCommand, ModifyFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory, ModifyFeaturePropertiesLiveCommandAnswerBuilder.EventFactory>
        implements ModifyFeaturePropertiesLiveCommandAnswerBuilder {

    private ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl(final ModifyFeaturePropertiesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl newInstance(
            final ModifyFeaturePropertiesLiveCommand command) {
        return new ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl(command);
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
        public ModifyFeaturePropertiesResponse created() {
            return ModifyFeaturePropertiesResponse.created(command.getThingId(), command.getFeatureId(),
                    command.getProperties(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyFeaturePropertiesResponse modified() {
            return ModifyFeaturePropertiesResponse.modified(command.getThingId(), command.getFeatureId(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featurePropertiesNotAccessibleError() {
            return errorResponse(command.getThingId(),
                    FeaturePropertiesNotAccessibleException.newBuilder(command.getThingId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featurePropertiesNotModifiableError() {
            return errorResponse(command.getThingId(),
                    FeaturePropertiesNotModifiableException.newBuilder(command.getThingId(),
                            command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeaturePropertiesCreated created() {
            return FeaturePropertiesCreated.of(command.getThingId(), command.getFeatureId(),
                    command.getProperties(), -1, Instant.now(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public FeaturePropertiesModified modified() {
            return FeaturePropertiesModified.of(command.getThingId(), command.getFeatureId(),
                    command.getProperties(), -1, Instant.now(), command.getDittoHeaders());
        }
    }

}
