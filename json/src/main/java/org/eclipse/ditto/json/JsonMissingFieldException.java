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
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Thrown if an expected JSON field is not in the JSON.
 */
public final class JsonMissingFieldException extends JsonRuntimeException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = "json.field.missing";

    private static final String MESSAGE_TEMPLATE = "JSON did not include required ''{0}'' field.";

    private static final String DEFAULT_DESCRIPTION = "Check if all required JSON fields were set.";

    private static final long serialVersionUID = -2569054723339845869L;

    private JsonMissingFieldException(final String message, final String description, final Throwable cause,
            final URI href) {
        super(ERROR_CODE, message, description, cause, href);
    }

    /**
     * Constructs a new {@code JsonMissingFieldException} object for the specified JSON pointer.
     *
     * @param pointer refers to the missing field.
     */
    public JsonMissingFieldException(final JsonPointer pointer) {
        this(MessageFormat.format(MESSAGE_TEMPLATE, pointer), DEFAULT_DESCRIPTION, null, null);
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
     *
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
         * Sets a message which points to the name of the missing field within a hierarchy. Thus if this method is
         * called, {@link #message} <p> Given the following valid JSON object: </p>
         * <p>
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
         * If, for example, the field {@code state} is missing the call to this method would be {@code
         * fieldName("attributes", "localSeason", "location", "state")}.
         *
         * @param missingFieldNameRoot the root of the hierarchy.
         * @param missingFieldNameChildren all children, grand children etc. of {@code missingFieldNameRoot}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if {@code missingFieldNameChildren} is {@code null}.
         */
        public Builder fieldName(final CharSequence missingFieldNameRoot,
                final CharSequence... missingFieldNameChildren) {
            requireNonNull(missingFieldNameChildren, "missingFieldNameChildren must not be null!");

            final CharSequence[] fieldNameHierarchy = new CharSequence[1 + missingFieldNameChildren.length];
            System.arraycopy(missingFieldNameChildren, 0, fieldNameHierarchy, 1, missingFieldNameChildren.length);
            fieldNameHierarchy[0] = missingFieldNameRoot;

            return fieldName(String.join(".", fieldNameHierarchy));
        }

        @Override
        protected JsonMissingFieldException doBuild(final String errorCode, final String message,
                final String description, final Throwable cause, final URI href) {
            return new JsonMissingFieldException(message, description, cause, href);
        }
    }

}
