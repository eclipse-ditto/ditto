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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.headers.conditional.ConditionalHeadersValidator;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingPreconditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingPreconditionNotModifiedException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;

/**
 * Provides a {@link ConditionalHeadersValidator} which checks conditional (http) headers based on a given ETag on
 * Thing resources.
 */
@Immutable
final class ThingsConditionalHeadersValidatorProvider {

    /**
     * Settings for validating conditional headers on Thing resources.
     */
    private static class ThingsConditionalHeadersValidationSettings
            implements ConditionalHeadersValidator.ValidationSettings {

        /**
         * Returns a builder for a {@link ThingPreconditionFailedException}.
         *
         * @param conditionalHeaderName the name of the conditional header.
         * @param expected the expected value.
         * @param actual the actual ETag value.
         * @return the builder.
         */
        @Override
        public DittoRuntimeExceptionBuilder<?> createPreconditionFailedExceptionBuilder(final String conditionalHeaderName,
                final String expected, final String actual) {
            return ThingPreconditionFailedException.newBuilder(conditionalHeaderName, expected, actual);
        }

        /**
         * Returns a builder for a {@link ThingPreconditionNotModifiedException}.
         *
         * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
         * @param matched the matched value.
         * @return the builder.
         */
        @Override
        public DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedExceptionBuilder(
                final String expectedNotToMatch, final String matched) {
            return ThingPreconditionNotModifiedException.newBuilder(expectedNotToMatch, matched);
        }

        @Override
        public DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedForEqualityExceptionBuilder() {
            return ThingPreconditionNotModifiedException.newBuilder()
                    .message("The previous value was equal to the new value and the 'if-equal' header was set to 'skip'.")
                    .description("Your changes were not applied, which is probably the expected outcome.");
        }
    }

    private static final Set<JsonPointer> EXEMPTED_FIELDS = Collections.singleton(JsonPointer.of("_policy"));
    private static final ConditionalHeadersValidator INSTANCE = createInstance();

    private ThingsConditionalHeadersValidatorProvider() {
        throw new AssertionError();
    }

    /**
     * Returns the (singleton) instance of {@link ConditionalHeadersValidator} for Thing resources.
     *
     * @return the {@link ConditionalHeadersValidator}.
     */
    public static ConditionalHeadersValidator getInstance() {
        return INSTANCE;
    }

    private static ConditionalHeadersValidator createInstance() {
        return ConditionalHeadersValidator.of(new ThingsConditionalHeadersValidationSettings(),
                ThingsConditionalHeadersValidatorProvider::skipExemptedFields);
    }

    /**
     * Skip precondition check if the selected fields contain exempted fields (e.g. {@code _policy} for things
     * because the revision of a thing does not change if its policy is updated).
     *
     * @param command the command to check for if the conditional header check should be skipped.
     * @return {@code true} when for the passed {@code command} the conditional header check should be skipped.
     */
    private static boolean skipExemptedFields(final Command<?> command) {

        @Nullable final JsonFieldSelector selectedFields;
        if (command instanceof RetrieveThing retrieveThing) {
            selectedFields = retrieveThing.getSelectedFields().orElse(null);
        } else if (command instanceof RetrieveThings retrieveThings) {
            selectedFields = retrieveThings.getSelectedFields().orElse(null);
        } else {
            return false;
        }

        if (null != selectedFields) {
            return containsExemptedField(selectedFields.getPointers());
        }

        return false;
    }

    private static boolean containsExemptedField(final Set<JsonPointer> selectedFields) {
        final Set<JsonPointer> result = new HashSet<>(EXEMPTED_FIELDS);
        result.retainAll(selectedFields);
        return !result.isEmpty();
    }

}
