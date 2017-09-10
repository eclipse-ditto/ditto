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
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

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
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to {@code
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

}
