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

import javax.annotation.Nonnull;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link
 * ModifyFeatureDefinition} commands.
 */
public interface ModifyFeatureDefinitionLiveCommandAnswerBuilder extends
        LiveCommandAnswerBuilder.ModifyCommandResponseStep<ModifyFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory, ModifyFeatureDefinitionLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyFeatureDefinition} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyFeatureDefinitionResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyFeatureDefinitionResponse created();

        /**
         * Builds a "modified"  {@link ModifyFeatureDefinitionResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        public ModifyFeatureDefinitionResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Feature Definition was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException
         * FeatureDefinitionNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse featureDefinitionNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the Feature Definition was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotModifiableException
         * FeatureDefinitionNotModifiableException
         */
        @Nonnull
        ThingErrorResponse featureDefinitionNotModifiableError();

    }

    /**
     * Factory for events triggered by {@link ModifyFeatureDefinition} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link FeatureDefinitionCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDefinitionCreated created();

        /**
         * Creates a {@link FeatureDefinitionModified} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        FeatureDefinitionModified modified();

    }

}
