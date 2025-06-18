/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.time.Instant;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.internal.utils.persistentactors.etags.AbstractConditionHeaderCheckingCommandStrategy;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.commands.WotValidationConfigCommand;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigPreconditionFailedException;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigPreconditionNotModifiedException;

/**
 * Abstract base class for strategies handling WoT validation config commands.
 *
 * @param <C> the type of the handled command
 * @since 3.8.0
 */
@Immutable
abstract class AbstractWotValidationConfigCommandStrategy<C extends WotValidationConfigCommand<C>>
        extends AbstractConditionHeaderCheckingCommandStrategy<C, WotValidationConfig, WotValidationConfigId, WotValidationConfigEvent<?>> {

    private static final ConditionalHeadersValidator VALIDATOR = ConditionalHeadersValidator.of(
            new ConditionalHeadersValidator.ValidationSettings() {
                @Override
                public DittoRuntimeExceptionBuilder<?> createPreconditionFailedExceptionBuilder(String conditionalHeaderName,
                        String expected, String actual) {
                    return WotValidationConfigPreconditionFailedException.newBuilder(conditionalHeaderName, expected, actual);
                }

                @Override
                public DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedExceptionBuilder(String expectedNotToMatch,
                        String matched) {
                    return WotValidationConfigPreconditionNotModifiedException.newBuilder(expectedNotToMatch, matched);
                }

                @Override
                public DittoRuntimeExceptionBuilder<?> createPreconditionFailedForEqualityExceptionBuilder() {
                    return WotValidationConfigPreconditionFailedException.newBuilder()
                            .message("The previous value was equal to the new value and the 'if-equal' header was set to 'skip'.")
                            .description("Your changes were not applied, which is probably the expected outcome.");
                }
            });

    protected AbstractWotValidationConfigCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    @Override
    public boolean isDefined(final C command) {
        return true;
    }

    @Override
    protected ConditionalHeadersValidator getValidator() {
        return VALIDATOR;
    }

    protected static Instant getEventTimestamp() {
        return Instant.now();
    }
}