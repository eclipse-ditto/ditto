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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Thrown if an expected JSON field is not in the JSON.
 */
public final class JsonMissingFieldException extends JsonRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.field.missing";

    private static final String MESSAGE_TEMPLATE = "JSON did not include required <{0}> field!";

    private static final String DEFAULT_DESCRIPTION = "Check if all required JSON fields were set.";

    private static final long serialVersionUID = -2569054723339845869L;

    private JsonMissingFieldException(@Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {

        super(ERROR_CODE, message, description, cause, href);
    }

    /**
     * Constructs a new {@code JsonMissingFieldException} object for the specified JSON key or pointer.
     *
     * @param key JsonKey or JsonPointer which refers to the missing field.
     */
    public JsonMissingFieldException(final CharSequence key) {
        this(MessageFormat.format(MESSAGE_TEMPLATE, key), DEFAULT_DESCRIPTION, null, null);
    }

    /**
     * Constructs a new {@code JsonMissingFieldException} object for the JsonPointer of the specified
     * JsonFieldDefinition.
     *
     * @param fieldDefinition provides the JsonPointer which refers to the missing field.
     * @throws NullPointerException if {@code jsonFieldDefinition} is {@code null}.
     */
    public JsonMissingFieldException(final JsonFieldDefinition<?> fieldDefinition) {
        this(requireNonNull(fieldDefinition, "The JSON Field Definition must not be null!").getPointer());
    }

    /**
     * Returns a builder for fluently creating instances of {@code JsonMissingFieldException}s..
     *
     * @return a new builder for JsonMissingFieldException objects.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A mutable builder for a {@code JsonMissingFieldException}.
     */
    @NotThreadSafe
    public static final class Builder extends AbstractJsonExceptionBuilder<JsonMissingFieldException> {

        private Builder() {
            super(ERROR_CODE);
            description(DEFAULT_DESCRIPTION);
        }

        /**
         * Sets a message which points to the name of the missing field. Thus if this method is called, {@link #message}
         * should not be called.
         *
         * @param missingFieldName the name of the missing field.
         * @return this builder to allow method chaining.
         */
        public Builder fieldName(final CharSequence missingFieldName) {
            message(MessageFormat.format(MESSAGE_TEMPLATE, missingFieldName));
            return this;
        }

        /**
         * Sets a message which points to the name of the missing field within a hierarchy.
         * <p>
         * Given the following valid JSON object:
         * </p>
         * <pre>
         *    {
         *       "attributes": {
         *          "localSeason": {
         *             "season": "autumn",
         *             "location": {
         *                "country": "Germany",
         *                "state": "Baden-Wuerttemberg",
         *                "place": "Leimerstetten"
         *             }
         *          }
         *       }
         *    }
         * </pre>
         * <p>
         * If, for example, the field {@code state} is missing the call to this method would be
         * {@code fieldName("attributes", "localSeason", "location", "state")}.
         * </p>
         *
         * @param missingFieldNameRoot the root of the hierarchy.
         * @param missingFieldNameChildren all children, grand children etc. of {@code missingFieldNameRoot}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @see #message(String)
         */
        public Builder fieldName(final CharSequence missingFieldNameRoot,
                final CharSequence... missingFieldNameChildren) {

            requireNonNull(missingFieldNameRoot, "The root of the field name hierarchy must not be null!");
            requireNonNull(missingFieldNameChildren, "The field name children must not be null!");

            final Collection<CharSequence> allFieldNames = new ArrayList<>(1 + missingFieldNameChildren.length);
            allFieldNames.add(missingFieldNameRoot);
            Collections.addAll(allFieldNames, missingFieldNameChildren);

            return fieldName(String.join(".", allFieldNames));
        }

        @Override
        protected JsonMissingFieldException doBuild(final String errorCode,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {

            return new JsonMissingFieldException(message, description, cause, href);
        }

    }

}
