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

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link
 * DeleteFeatureProperties} commands.
 */
public interface DeleteFeaturePropertiesLiveCommandAnswerBuilder
        extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<DeleteFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory,
                        DeleteFeaturePropertiesLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link DeleteFeatureProperties} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a {@link DeleteFeaturePropertiesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        DeleteFeaturePropertiesResponse deleted();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature properties were not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException
         * FeaturePropertiesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featurePropertiesNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature properties were not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotModifiableException
         * FeaturePropertiesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featurePropertiesNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link DeleteFeatureProperties} command.
     */
    @SuppressWarnings("squid:S1609")
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeaturePropertiesDeleted} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeaturePropertiesDeleted deleted();
    }

}
