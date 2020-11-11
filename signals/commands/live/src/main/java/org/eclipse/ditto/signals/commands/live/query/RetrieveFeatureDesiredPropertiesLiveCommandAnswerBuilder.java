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
package org.eclipse.ditto.signals.commands.live.query;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertiesResponse;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s for {@link RetrieveFeatureDesiredProperties} commands.
 *
 * @since 1.5.0
 */
public interface RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.QueryCommandResponseStep<RetrieveFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link RetrieveFeatureDesiredProperties} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Creates a {@link RetrieveFeatureDesiredPropertiesResponse} containing the retrieved value for the {@link
         * RetrieveFeatureDesiredProperties} command.
         *
         * @param featureDesiredProperties the value of the requested Feature desired properties
         * @return a response containing the requested value
         * @throws NullPointerException if {@code FeatureDesiredProperties} is {@code null}
         */
        @Nonnull
        RetrieveFeatureDesiredPropertiesResponse retrieved(FeatureProperties featureDesiredProperties);

        /**
         * Creates a {@link ThingErrorResponse} specifying that no Feature desired property exist or the requesting user does
         * not have enough permission to retrieve them.
         *
         * @return the error response
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertiesNotAccessibleError();
    }

}
