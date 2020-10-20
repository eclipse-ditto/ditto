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
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeatureDesiredPropertiesModified;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link
 * ModifyFeatureDesiredProperties} commands.
 *
 * @since 1.4.0
 */
public interface ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder
        extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureDesiredPropertiesLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyFeatureDesiredProperties} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyFeatureDesiredPropertiesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureDesiredPropertiesResponse created();

        /**
         * Builds a "modified"  {@link ModifyFeatureDesiredPropertiesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        public ModifyFeatureDesiredPropertiesResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature's desired properties were not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotAccessibleException
         * FeatureDesiredPropertiesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertiesNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the feature's desired properties were not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDesiredPropertiesNotModifiableException
         * FeatureDesiredPropertiesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureDesiredPropertiesNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link ModifyFeatureDesiredProperties} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDesiredPropertiesCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDesiredPropertiesCreated created();

        /**
         * Creates a {@link FeatureDesiredPropertiesModified} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDesiredPropertiesModified modified();
    }

}
