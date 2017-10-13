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
import javax.annotation.ParametersAreNonnullByDefault;

import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;

import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswerBuilder;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandResponseFactory;
import org.eclipse.ditto.signals.commands.live.base.LiveEventFactory;

/**
 * LiveCommandAnswer builder for producing {@code CommandResponse}s and {@code Event}s for {@link ModifyAttribute}
 * commands.
 */
public interface ModifyAttributeLiveCommandAnswerBuilder extends LiveCommandAnswerBuilder.ModifyCommandResponseStep<
        ModifyAttributeLiveCommandAnswerBuilder.ResponseFactory,
        ModifyAttributeLiveCommandAnswerBuilder.EventFactory> {

    /**
     * Factory for {@code CommandResponse}s to {@link ModifyAttribute} command.
     */
    @ParametersAreNonnullByDefault
    interface ResponseFactory extends LiveCommandResponseFactory {

        /**
         * Builds a "created"  {@link ModifyAttributeResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyAttributeResponse created();

        /**
         * Builds a "modified"  {@link ModifyAttributeResponse} using the values of the {@code Command}.
         *
         * @return the response.
         */
        @Nonnull
        ModifyAttributeResponse modified();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attribute was not accessible.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException
         * AttributeNotAccessibleException
         */
        @Nonnull
        ThingErrorResponse attributeNotAccessibleError();

        /**
         * Builds a {@link ThingErrorResponse} indicating that the attribute was not modifiable.
         *
         * @return the response.
         * @see org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotModifiableException
         * AttributeNotModifiableException
         */
        @Nonnull
        ThingErrorResponse attributeNotModifiableError();
    }

    /**
     * Factory for events triggered by {@link ModifyAttribute} command.
     */
    interface EventFactory extends LiveEventFactory {

        /**
         * Creates a {@link AttributeCreated} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        AttributeCreated created();

        /**
         * Creates a {@link AttributeModified} event using the values of the {@code Command}.
         *
         * @return the event.
         */
        @Nonnull
        AttributeModified modified();
    }

}
