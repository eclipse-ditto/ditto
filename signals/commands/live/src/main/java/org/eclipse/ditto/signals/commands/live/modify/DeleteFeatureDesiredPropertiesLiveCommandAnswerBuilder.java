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

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesDeleted;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link
 * DeleteFeatureDesiredProperties} commands.
 *
 * @since 1.5.0
 */
public interface DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder
        extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory,
                DeleteFeatureDesiredPropertiesLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteFeatureDesiredProperties} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteFeatureDesiredPropertiesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteFeatureDesiredPropertiesResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the features desired properties were not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotAccessibleException
         * FeatureDesiredPropertiesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertiesNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature properties were not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotModifiableException
         * FeatureDesiredPropertiesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertiesNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link DeleteFeatureDesiredProperties} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDesiredPropertiesDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDesiredPropertiesDeleted deleted();
    }

}
