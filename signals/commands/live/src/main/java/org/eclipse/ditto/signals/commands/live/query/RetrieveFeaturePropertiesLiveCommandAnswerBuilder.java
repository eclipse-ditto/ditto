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

import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeatureProperties} commands.
 */
public interface RetrieveFeaturePropertiesLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeatureProperties} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link RetrieveFeaturePropertiesResponse} containing the retrieved value for the {@link
         * RetrieveFeatureProperties} command.
         *
         * @param featureProperties the value of the requested Feature properties
         * @return a response containing the requested value
         * @throws NullPointerException if {@code featureProperties} is {@code null}
         */
        @Nonnull
        RetrieveFeaturePropertiesResponse retrieved(FeatureProperties featureProperties);

        /**
         * Creates a {@link ThingErrorResponse} specifying that no Feature property exist or the requesting user does
         * not have enough permission to retrieve them.
         *
         * @return the error response
         */
        @Nonnull
        ThingErrorResponse featurePropertiesNotAccessibleError();
    }

}
