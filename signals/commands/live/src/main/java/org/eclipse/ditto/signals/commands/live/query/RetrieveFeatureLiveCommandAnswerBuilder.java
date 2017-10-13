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

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeature} commands.
 */
public interface RetrieveFeatureLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeatureLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeature} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link RetrieveFeatureResponse} containing the retrieved value for the {@link RetrieveFeature}
         * command.
         *
         * @param feature the value of the requested Feature.
         * @return the response.
         * @throws NullPointerException if {@code feature} is {@code null}.
         */
        @Nonnull
        RetrieveFeatureResponse retrieved(Feature feature);

        /**
         * Creates a {@link ThingErrorResponse} specifying that the requested feature does not exist or the requesting
         * user does not have enough permission to retrieve them.
         *
         * @return the response.
         */
        @Nonnull
        ThingErrorResponse featureNotAccessibleError();
    }

}
