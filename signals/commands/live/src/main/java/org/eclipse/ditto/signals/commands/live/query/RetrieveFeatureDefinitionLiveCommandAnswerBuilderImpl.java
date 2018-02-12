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

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.live.query.RetrieveFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * A mutable builder with a fluent API for creating a {@link LiveCommandAnswer} for a
 * {@link RetrieveFeatureDefinitionLiveCommand}.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
final class RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl
        extends AbstractLiveCommandAnswerBuilder<RetrieveFeatureDefinitionLiveCommand, ResponseFactory>
        implements RetrieveFeatureDefinitionLiveCommandAnswerBuilder {

    private RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl(final RetrieveFeatureDefinitionLiveCommand command) {
        super(command);
    }

    /**
     * Returns a new instance of {@code RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl}.
     *
     * @param command the command to build an answer for.
     * @return the instance.
     * @throws NullPointerException if {@code command} is {@code null}.
     */
    public static RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl newInstance(
            final RetrieveFeatureDefinitionLiveCommand command) {

        return new RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl(command);
    }

    @Override
    protected CommandResponse doCreateResponse(
            final Function<ResponseFactory, CommandResponse<?>> createResponseFunction) {

        return createResponseFunction.apply(new ResponseFactoryImpl());
    }

    @AllValuesAreNonnullByDefault
    @Immutable
    private final class ResponseFactoryImpl implements ResponseFactory {

        @Override
        public RetrieveFeatureDefinitionResponse retrieved(final FeatureDefinition featureProperties) {
            return RetrieveFeatureDefinitionResponse.of(command.getThingId(), command.getFeatureId(), featureProperties,
                    command.getDittoHeaders());
        }

        @Override
        public ThingErrorResponse featureDefinitionNotAccessibleError() {
            return errorResponse(command.getThingId(),
                    FeatureDefinitionNotAccessibleException.newBuilder(command.getThingId(), command.getFeatureId())
                            .dittoHeaders(command.getDittoHeaders())
                            .build());
        }

    }

}
