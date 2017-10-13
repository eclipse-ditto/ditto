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
package org.eclipse.ditto.signals.commands.live.query;

import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a {@link
 * RetrieveFeaturesLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveFeaturesLiveCommandAnswerBuilderImpl
        extends
        AbstractLiveCommandAnswerBuilder<RetrieveFeaturesLiveCommand, RetrieveFeaturesLiveCommandAnswerBuilder.ResponseFactory>
        implements RetrieveFeaturesLiveCommandAnswerBuilder {

    private RetrieveFeaturesLiveCommandAnswerBuilderImpl(final RetrieveFeaturesLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveFeaturesLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveFeaturesLiveCommandAnswerBuilderImpl newInstance(final RetrieveFeaturesLiveCommand command) {
        return new RetrieveFeaturesLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {
        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @ParametersAreNonnullByDefault
    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Nonnull
        @Override
        public RetrieveFeaturesResponse retrieved(final Features features) {
            return RetrieveFeaturesResponse.of(command.getThingId(), features, command.getDittoHeaders());
        }

        @Nonnull
        @Override
        public ThingErrorResponse featuresNotAccessibleError() {
            return errorResponse(command.getThingId(), FeaturesNotAccessibleException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }
    }

}
