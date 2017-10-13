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

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesModified;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link ModifyAttributes}
 * commands.
 */
public interface ModifyAttributesLiveCommandAnswerBuilder extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        ModifyAttributesLiveCommandAnswerBuilder.ResponseFactory, ModifyAttributesLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyAttributes} command.
     */
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyAttributesResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyAttributesResponse created();

        /**
         * Builds a "modified"  {@link ModifyAttributesResponse} using the values of the {@code Command}.
         *
         * @return the response
         */
        @Nonnull
        ModifyAttributesResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attributes were not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException
         * AttributesNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse attributesNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attributes were not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotModifiableException
         * AttributesNotModifiableException
         */
        @Nonnull
        ThingErrorResponse attributesNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link ModifyAttributes} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link AttributesCreated} event using the values of the {@code Command}.
         *
         * @return the AttributesCreated event
         */
        @Nonnull
        AttributesCreated created();

        /**
         * Creates a {@link AttributesModified} event using the values of the {@code Command}.
         *
         * @return the AttributesModified event
         */
        @Nonnull
        AttributesModified modified();
    }

}
