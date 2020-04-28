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
package org.eclipse.ditto.services.utils.headers.conditional;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;


/**
 * Checks conditional (http) headers based on a given ETag. Has to be configured with {@link ValidationSettings}.
 */
@Immutable
public final class ConditionalHeadersValidator {

    /**
     * Service-specific validation handling. E.g., in case of a validation failure, a different exception should be
     * thrown for things- and policies-service
     */
    public interface ValidationSettings {

        /**
         * Provides a builder for a {@link DittoRuntimeException} in case status {@code 412 (Precondition Failed)}
         * should be returned.
         *
         * @param conditionalHeaderName the name of the conditional header.
         * @param expected the expected value.
         * @param actual the actual ETag value.
         * @return the builder.
         */
        DittoRuntimeExceptionBuilder createPreconditionFailedExceptionBuilder(final String conditionalHeaderName,
                final String expected, final String actual);

        /**
         * Returns a builder for a {@link DittoRuntimeException} in case status {@code 304 (Not Modified)} should be
         * returned.
         *
         * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
         * @param matched the matched value.
         * @return the builder.
         */
        DittoRuntimeExceptionBuilder createPreconditionNotModifiedExceptionBuilder(final String expectedNotToMatch,
                final String matched);
    }

    private final ValidationSettings validationSettings;

    private static final Set<JsonPointer> EXEMPTED_FIELDS = Collections.singleton(JsonPointer.of("_policy"));
    private static final String SELECTED_FIELDS = "selectedFields";

    private ConditionalHeadersValidator(final ValidationSettings validationSettings) {
        this.validationSettings = validationSettings;
    }

    /**
     * Creates a new validator instance with the given {@code settings}.
     *
     * @param validationSettings the settings.
     * @return the created instance.
     */
    public static ConditionalHeadersValidator of(final ValidationSettings validationSettings) {
        return new ConditionalHeadersValidator(requireNonNull(validationSettings));
    }

    /**
     * Checks if the in the given {@code command} contained
     * {@link org.eclipse.ditto.services.utils.headers.conditional.PreconditionHeader precondition headers} meet their
     * condition for the given entity tag. Throws an instance of
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException} as defined in {@link ValidationSettings}.
     * when a condition fails.
     *
     * @param command The command that contains the headers.
     * @param currentETagValue the entity-tag of the entity targeted by the given {@code command}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoRuntimeException when a condition fails (the concrete
     * subclass is defined by {@link ValidationSettings}).
     */
    public void checkConditionalHeaders(final Command command,
            @Nullable final EntityTag currentETagValue) {

        if (skipPreconditionHeaderCheck(command, currentETagValue)) {
            return;
        }

        checkIfMatch(command, currentETagValue);
        checkIfNoneMatch(command, currentETagValue);

    }

    private boolean skipPreconditionHeaderCheck(final Command command, @Nullable final EntityTag
            currentETagValue) {
        return currentETagValue == null &&
                (Command.Category.DELETE.equals(command.getCategory()) ||
                        Command.Category.QUERY.equals(command.getCategory())) || skipExemptedFields(command);
    }

    /**
     * Skip precondition check if the selected fields contain exempted fields (e.g. {@code _policy} for things
     * because the revision of a thing does not change if its policy is updated).
     */
    private boolean skipExemptedFields(final Command command) {

        @Nullable JsonFieldSelector selectedFields;
        if (command instanceof RetrieveThing) {
            selectedFields = ((RetrieveThing) command).getSelectedFields().orElse(null);
        } else if (command instanceof RetrieveThings) {
            selectedFields = ((RetrieveThings) command).getSelectedFields().orElse(null);
        } else if (command instanceof SudoRetrieveThing) {
            selectedFields = ((SudoRetrieveThing) command).getSelectedFields().orElse(null);
        } else if (command instanceof SudoRetrieveThings) {
            selectedFields = ((SudoRetrieveThings) command).getSelectedFields().orElse(null);
        } else {
            return false;
        }

        if (selectedFields != null) {
            return this.containsExemptedField(selectedFields.getPointers());
        }

        return false;

    }

    private boolean containsExemptedField(final Set<JsonPointer> selectedFields) {
        final Set<JsonPointer> result = new HashSet<>(EXEMPTED_FIELDS);
        result.retainAll(selectedFields);
        return !result.isEmpty();
    }

    private void checkIfMatch(final Command command, @Nullable final EntityTag currentETagValue) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(ifMatch -> {
            if (!ifMatch.meetsConditionFor(currentETagValue)) {
                throw buildPreconditionFailedException(ifMatch, dittoHeaders, currentETagValue);
            }
        });
    }

    private void checkIfNoneMatch(final Command command, @Nullable final EntityTag currentETagValue) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(ifNoneMatch -> {
            if (!ifNoneMatch.meetsConditionFor(currentETagValue)) {
                if (Command.Category.QUERY.equals(command.getCategory())) {
                    throw buildNotModifiedException(ifNoneMatch, dittoHeaders, currentETagValue);
                } else {
                    throw buildPreconditionFailedException(ifNoneMatch, dittoHeaders, currentETagValue);
                }
            }
        });
    }

    private DittoRuntimeException buildPreconditionFailedException(
            final PreconditionHeader preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        final String headerKey = preconditionHeader.getKey();
        final String headerValue = preconditionHeader.getValue();

        return validationSettings
                .createPreconditionFailedExceptionBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private DittoRuntimeException buildNotModifiedException(final PreconditionHeader preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        return validationSettings
                .createPreconditionNotModifiedExceptionBuilder(preconditionHeader.getValue(),
                        String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private DittoHeaders appendETagIfNotNull(final DittoHeaders dittoHeaders, @Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder().eTag(entityTag).build();
    }


}
