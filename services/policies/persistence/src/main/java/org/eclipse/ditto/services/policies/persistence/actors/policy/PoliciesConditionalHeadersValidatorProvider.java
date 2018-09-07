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
package org.eclipse.ditto.services.policies.persistence.actors.policy;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.services.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyPreconditionNotModifiedException;

/**
 * Provides a {@link ConditionalHeadersValidator} which checks conditional (http) headers based on a given ETag on
 * Policy resources.
 */
@Immutable
final class PoliciesConditionalHeadersValidatorProvider {

    /**
     * Settings for validating conditional headers on Policies resources.
     */
    private static class PoliciesConditionalHeadersValidationSettings
            implements ConditionalHeadersValidator.ValidationSettings {

        /**
         * Returns a builder for a {@link PolicyPreconditionFailedException}.
         * @param conditionalHeaderName the name of the conditional header.
         * @param expected the expected value.
         * @param actual the actual ETag value.
         * @return the builder.
         */
        @Override
        public DittoRuntimeExceptionBuilder createPreconditionFailedExceptionBuilder(final String conditionalHeaderName,
                final String expected, final String actual) {
            return PolicyPreconditionFailedException.newBuilder(conditionalHeaderName, expected, actual);
        }

        /**
         * Returns a builder for a {@link PolicyPreconditionNotModifiedException}.
         * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
         * @param matched the matched value.
         * @return the builder.
         */
        @Override
        public DittoRuntimeExceptionBuilder createPreconditionNotModifiedExceptionBuilder(
                final String expectedNotToMatch, final String matched) {
            return PolicyPreconditionNotModifiedException.newBuilder(expectedNotToMatch, matched);
        }
    }

    private static final ConditionalHeadersValidator INSTANCE = createInstance();

    private PoliciesConditionalHeadersValidatorProvider() {
        throw new AssertionError();
    }

    /**
     * Returns the (singleton) instance of {@link ConditionalHeadersValidator} for Policies resources.
     * @return the {@link ConditionalHeadersValidator}.
     */
    public static ConditionalHeadersValidator getInstance() {
        return INSTANCE;
    }

    private static ConditionalHeadersValidator createInstance() {
        return ConditionalHeadersValidator.of(new PoliciesConditionalHeadersValidationSettings());
    }

}
