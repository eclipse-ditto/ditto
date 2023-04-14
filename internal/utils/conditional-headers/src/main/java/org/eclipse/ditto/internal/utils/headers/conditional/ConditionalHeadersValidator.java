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
package org.eclipse.ditto.internal.utils.headers.conditional;

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.signals.commands.Command;


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
        DittoRuntimeExceptionBuilder<?> createPreconditionFailedExceptionBuilder(String conditionalHeaderName,
                String expected, String actual);

        /**
         * Returns a builder for a {@link DittoRuntimeException} in case status {@code 304 (Not Modified)} should be
         * returned.
         *
         * @param expectedNotToMatch the value which was expected not to match {@code matched} value.
         * @param matched the matched value.
         * @return the builder.
         */
        DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedExceptionBuilder(String expectedNotToMatch,
                String matched);

        /**
         * Returns a builder for a {@link DittoRuntimeException} in case status {@code 304 (Not Modified)} should be
         * returned for when an updated value was equal to its previous value and the {@code if-equal} condition was
         * set to "skip".
         *
         * @return the builder.
         * @since 3.3.0
         */
        DittoRuntimeExceptionBuilder<?> createPreconditionNotModifiedForEqualityExceptionBuilder();
    }

    private final ValidationSettings validationSettings;
    private final Predicate<Command<?>> additionalSkipPreconditionHeaderCheckPredicate;

    private ConditionalHeadersValidator(final ValidationSettings validationSettings,
            final Predicate<Command<?>> additionalSkipPreconditionHeaderCheckPredicate) {
        this.validationSettings = validationSettings;
        this.additionalSkipPreconditionHeaderCheckPredicate = additionalSkipPreconditionHeaderCheckPredicate;
    }

    /**
     * Creates a new validator instance with the given {@code settings}.
     *
     * @param validationSettings the settings.
     * @return the created instance.
     */
    public static ConditionalHeadersValidator of(final ValidationSettings validationSettings) {
        return new ConditionalHeadersValidator(requireNonNull(validationSettings), cmd -> false);
    }

    /**
     * Creates a new validator instance with the given {@code settings}.
     *
     * @param validationSettings the settings.
     * @param additionalSkipPreconditionCheckPredicate a predicate accepting a Command which - when evaluated to
     * {@code true} - will skip the precondition check (in addition to the built-in check).
     * @return the created instance.
     */
    public static ConditionalHeadersValidator of(final ValidationSettings validationSettings,
            final Predicate<Command<?>> additionalSkipPreconditionCheckPredicate) {
        return new ConditionalHeadersValidator(requireNonNull(validationSettings),
                additionalSkipPreconditionCheckPredicate);
    }

    /**
     * Checks if the in the given {@code command} contained
     * {@link PreconditionHeader precondition headers} meet their
     * condition for the given entity tag. Throws an instance of
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} as defined in {@link ValidationSettings}.
     * when a condition fails.
     *
     * @param command The command that contains the headers.
     * @param currentETagValue the entity-tag of the entity targeted by the given {@code command}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException when a condition fails (the concrete
     * subclass is defined by {@link ValidationSettings}).
     */
    public void checkConditionalHeaders(final Command<?> command,
            @Nullable final EntityTag currentETagValue) {

        if (skipPreconditionHeaderCheck(command, currentETagValue)) {
            return;
        }

        checkIfMatch(command, currentETagValue);
        checkIfNoneMatch(command, currentETagValue);
    }

    /**
     * Checks if the in the given {@code command} contained
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaderDefinition#IF_EQUAL if-equal header} defines whether to
     * skip an update for when the value to update is the same as before.
     * Throws a "*PreconditionNotModifiedException" for the respective entity.
     *
     * @param command The command that potentially contains the if-equal header.
     * @param currentEntity the current entity targeted by the given {@code command}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoRuntimeException when a condition fails (the concrete
     * subclass is defined by {@link ValidationSettings}).
     */
    public <C extends Command<?>> C applyIfEqualHeader(final C command, @Nullable final Entity<?> currentEntity) {

        if (skipPreconditionHeaderCheck(command, null)) {
            return command;
        }

        return applyIfEqual(command, currentEntity);
    }

    private boolean skipPreconditionHeaderCheck(final Command<?> command, @Nullable final EntityTag
            currentETagValue) {
        return (currentETagValue == null &&
                (Command.Category.DELETE.equals(command.getCategory()) ||
                        Command.Category.QUERY.equals(command.getCategory()))
        ) || additionalSkipPreconditionHeaderCheckPredicate.test(command);
    }

    private void checkIfMatch(final Command<?> command, @Nullable final EntityTag currentETagValue) {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).ifPresent(ifMatch -> {
            if (!ifMatch.meetsConditionFor(currentETagValue)) {
                throw buildPreconditionFailedException(ifMatch, dittoHeaders, currentETagValue);
            }
        });
    }

    private void checkIfNoneMatch(final Command<?> command, @Nullable final EntityTag currentETagValue) {
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

    private <C extends Command<?>> C applyIfEqual(final C command, @Nullable final Entity<?> entity) {
        return IfEqualPreconditionHeader.fromDittoHeaders(command, validationSettings)
                .map(ifEqual -> ifEqual.handleCommand(() -> ifEqual.meetsConditionFor(entity)))
                .orElse(command);
    }

    private DittoRuntimeException buildPreconditionFailedException(
            final PreconditionHeader<?> preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        final String headerKey = preconditionHeader.getKey();
        final String headerValue = preconditionHeader.getValue();

        return validationSettings
                .createPreconditionFailedExceptionBuilder(headerKey, headerValue, String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private DittoRuntimeException buildNotModifiedException(final PreconditionHeader<?> preconditionHeader,
            final DittoHeaders dittoHeaders, @Nullable final EntityTag currentETagValue) {
        return validationSettings
                .createPreconditionNotModifiedExceptionBuilder(preconditionHeader.getValue(),
                        String.valueOf(currentETagValue))
                .dittoHeaders(appendETagIfNotNull(dittoHeaders, currentETagValue))
                .build();
    }

    private DittoRuntimeException buildNotModifiedForEqualityException() {
        return validationSettings
                .createPreconditionNotModifiedForEqualityExceptionBuilder()
                .build();
    }

    private DittoHeaders appendETagIfNotNull(final DittoHeaders dittoHeaders, @Nullable final EntityTag entityTag) {
        if (entityTag == null) {
            return dittoHeaders;
        }
        return dittoHeaders.toBuilder().eTag(entityTag).build();
    }

}
