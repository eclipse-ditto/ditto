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

import java.time.Instant;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesModified;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * ModifyFeaturesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class ModifyFeaturesLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<ModifyFeaturesLiveCommand, ModifyFeaturesLiveCommandAnswerBuilder.ResponseFactory, ModifyFeaturesLiveCommandAnswerBuilder.EventFactory>
        implements ModifyFeaturesLiveCommandAnswerBuilder {

    private ModifyFeaturesLiveCommandAnswerBuilderImpl(final ModifyFeaturesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code ModifyFeaturesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static ModifyFeaturesLiveCommandAnswerBuilderImpl newInstance(final ModifyFeaturesLiveCommand command) {
        return new ModifyFeaturesLiveCommandAnswerBuilderImpl(command);
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
        public ModifyFeaturesResponse created() {
            return ModifyFeaturesResponse.created(command.getThingEntityId(), command.getFeatures(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ModifyFeaturesResponse modified() {
            return ModifyFeaturesResponse.modified(command.getThingEntityId(), command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featuresNotAccessibleError() {
            return errorResponse(command.getThingEntityId(),
                    FeaturesNotAccessibleException.newBuilder(command.getThingEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featuresNotModifiableError() {
            return errorResponse(command.getThingEntityId(),
                    FeaturesNotModifiableException.newBuilder(command.getThingEntityId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

    @Immutable
    private final class EventFactoryImpl implements EventFactory {

        @Nonnull
        @Override
        public FeaturesCreated created() {
            return FeaturesCreated.of(command.getThingEntityId(), command.getFeatures(), -1, Instant.now(),
                    command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public FeaturesModified modified() {
            return FeaturesModified.of(command.getThingEntityId(), command.getFeatures(), -1, Instant.now(),
                    command.getDittoHeaders());
        }
    }

}
