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
package org.eclipse.ditto.base.model.headers;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;

/**
 * Provides validators for header values.
 * Those validators may be composed to create a validator chain (see {@link org.eclipse.ditto.base.model.headers.ValueValidator#andThen(org.eclipse.ditto.base.model.headers.ValueValidator)}.
 *
 * @since 1.1.0
 */
@Immutable
public final class HeaderValueValidators {

    private HeaderValueValidators() {
        throw new AssertionError();
    }

    /**
     * Returns a validator which never throws an exception.
     *
     * @return the validator.
     */
    public static ValueValidator getNoOpValidator() {
        return new AbstractHeaderValueValidator(valueType -> true) {
            @Override
            protected void validateValue(final HeaderDefinition definition, final CharSequence value) {
                // do nothing
            }
        };
    }

    /**
     * Returns a validator for checking that a CharSequence was non-empty.
     *
     * @return the validator.
     * @since 1.3.0
     */
    public static ValueValidator getNonEmptyValidator() {
        return NonEmptyValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is an integer value.
     *
     * @return the validator.
     */
    public static ValueValidator getIntValidator() {
        return IntValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is a {@code long} value.
     *
     * @return the validator.
     */
    public static ValueValidator getLongValidator() {
        return LongValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is a {@code boolean} value.
     *
     * @return the validator.
     */
    public static ValueValidator getBooleanValidator() {
        return BooleanValueValidator.getInstance();
    }

    /**
     * Returns a validator checking a header value against the string representation of members of an enum class.
     *
     * @param enumValues The values of the enum class.
     * @return The validator.
     * @since 2.3.0
     */
    public static ValueValidator getEnumValidator(final Enum<?>[] enumValues) {
        return EnumValueValidator.getInstance(enumValues);
    }

    /**
     * Returns a validator for checking if a CharSequence represents a {@link org.eclipse.ditto.json.JsonArray} which
     * <em>contains only string items.</em>
     *
     * @return the validator.
     */
    public static ValueValidator getJsonArrayValidator() {
        return JsonArrayValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence represents a {@link org.eclipse.ditto.json.JsonObject}.
     *
     * @return the validator.
     */
    public static ValueValidator getJsonObjectValidator() {
        return JsonObjectValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is valid according to
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTag#isValid(CharSequence)}.
     *
     * @return the validator.
     */
    static ValueValidator getEntityTagValidator() {
        return EntityTagValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is valid according to
     * {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher#isValid(CharSequence)}.
     *
     * @return the validator.
     */
    static ValueValidator getEntityTagMatcherValidator() {
        return EntityTagMatcherValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence is a comma-separated list of EntityTagMatchers which are valid
     * according to {@link org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatcher#isValid(CharSequence)}.
     *
     * @return the validator.
     */
    static ValueValidator getEntityTagMatchersValidator() {
        return EntityTagMatchersValueValidator.getInstance(
                EntityTagMatcherValueValidator.getInstance(EntityTagMatchers.class::equals));
    }

    /**
     * Returns a validator that checks whether a char sequence is a valid json array of acknowledgement labels
     *
     * @return the validator.
     * @since 1.4.0
     */
    static ValueValidator getRequestedAcksValueValidator() {
        return RequestedAcksValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence represents a
     * {@link org.eclipse.ditto.base.model.common.DittoDuration}.
     *
     * @return the validator.
     */
    static ValueValidator getDittoDurationValidator() {
        return DittoDurationValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence represents a timeout in form of a
     * {@link org.eclipse.ditto.base.model.common.DittoDuration}.
     *
     * @return the validator.
     * @since 1.2.0
     */
    static ValueValidator getTimeoutValueValidator() {
        return TimeoutValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence represents a
     * {@link org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders} to put.
     *
     * @return the validator.
     * @since 1.2.0
     */
    static ValueValidator getMetadataHeadersValidator() {
        return MetadataHeadersValueValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a CharSequence represents a {@link org.eclipse.ditto.json.JsonFieldSelector}.
     *
     * @return the validator.
     * @since 3.0.0
     */
    static ValueValidator getJsonFieldSelectorValidator() {
        return JsonFieldSelectorValidator.getInstance();
    }

    /**
     * Returns a validator for checking if a normalized CharSequence is equal to
     * {@value DittoChannelValueValidator#CHANNEL_TWIN} or {@value DittoChannelValueValidator#CHANNEL_LIVE}.
     * Normalized in this context means trimmed and converted to lower case.
     * Normalization is temporarily conducted by the returned validator for validation only.
     *
     * @return the validator.
     * @since 2.3.0
     */
    static ValueValidator getDittoChannelValidator() {
        return DittoChannelValueValidator.getInstance();
    }

}
