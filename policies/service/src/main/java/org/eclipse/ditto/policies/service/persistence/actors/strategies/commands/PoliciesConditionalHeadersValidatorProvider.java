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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException;

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
        public DittoRuntimeExceptionBuilder<?> createPreconditionFailedExceptionBuilder(final String conditionalHeaderName,
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
        public DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedExceptionBuilder(
                final String expectedNotToMatch, final String matched) {
            return PolicyPreconditionNotModifiedException.newBuilder(expectedNotToMatch, matched);
        }

        @Override
        public DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedForEqualityExceptionBuilder() {
            return PolicyPreconditionNotModifiedException.newBuilder()
                    .message("The previous value was equal to the new value and the 'if-equal' header was set to 'skip'.")
                    .description("Your changes were not applied, which is probably the expected outcome.");
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
