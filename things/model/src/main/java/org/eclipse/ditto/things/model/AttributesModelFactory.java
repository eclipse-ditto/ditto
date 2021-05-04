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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonPointerInvalidException;

/**
 * Factory that creates new {@code attributes} objects.
 */
@Immutable
public final class AttributesModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private AttributesModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new immutable empty {@link Attributes}.
     *
     * @return the new immutable empty {@code Attributes}.
     */
    public static Attributes emptyAttributes() {
        return ImmutableAttributes.empty();
    }

    /**
     * Returns a new immutable {@link Attributes} which represents {@code null}.
     *
     * @return the new {@code null}-like {@code Attributes}.
     */
    public static Attributes nullAttributes() {
        return NullAttributes.newInstance();
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code Attributes}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws JsonPointerInvalidException if an attribute name in the contained {@code jsonObject} was not valid
     * according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static Attributes newAttributes(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object for initialization");

        if (!jsonObject.isNull()) {
            return ImmutableAttributes.of(jsonObject);
        } else {
            return nullAttributes();
        }
    }

    /**
     * Returns a new immutable {@link Attributes} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code Attributes}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to {@code
     * Attributes}.
     */
    public static Attributes newAttributes(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newAttributes(jsonObject);
    }

    /**
     * Returns a new empty builder for a {@link Attributes}.
     *
     * @return the builder.
     */
    public static AttributesBuilder newAttributesBuilder() {
        return ImmutableAttributesBuilder.empty();
    }

    /**
     * Returns a new builder for a {@link Attributes} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the builder.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static AttributesBuilder newAttributesBuilder(final JsonObject jsonObject) {
        return ImmutableAttributesBuilder.of(jsonObject);
    }

    /**
     * Validates the given attribute {@link JsonPointer}.
     *
     * @param jsonPointer {@code jsonPointer} that is validated
     * @return the same {@code jsonPointer} if validation was successful
     * @throws JsonKeyInvalidException if {@code jsonPointer} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.2.0
     */
    public static JsonPointer validateAttributePointer(final JsonPointer jsonPointer) {
        return JsonKeyValidator.validate(jsonPointer);
    }

    /**
     * Validates the given attribute {@link JsonObject}.
     *
     * @param jsonObject {@code jsonObject} that is validated
     * @throws JsonKeyInvalidException if {@code jsonObject} was not valid according to
     * pattern {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.3.0
     */
    public static void validateAttributeKeys(final JsonObject jsonObject) {
        JsonKeyValidator.validateJsonKeys(jsonObject);
    }
}
