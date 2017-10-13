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
package org.eclipse.ditto.services.gateway.security.jwt;

import java.math.BigInteger;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;

/**
 * Representation for a JSON Web Key.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7517">JSON Web Key (JWK)</a>
 */
public interface JsonWebKey {

    /**
     * Returns the key type parameter.
     *
     * @return the key type.
     */
    String getType();

    /**
     * Returns the algorithm parameter.
     *
     * @return the algorithm.
     */
    Optional<String> getAlgorithm();

    /**
     * Returns the usage parameter.
     *
     * @return the usage.
     */
    Optional<String> getUsage();

    /**
     * Returns the id parameter.
     *
     * @return the id.
     */
    String getId();

    /**
     * Returns the modulus parameter.
     *
     * @return the modulus.
     */
    BigInteger getModulus();

    /**
     * Returns the exponent parameter.
     *
     * @return the exponent.
     */
    BigInteger getExponent();

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a JSON Web Key.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the key's type.
         */
        public static final JsonFieldDefinition<String> KEY_TYPE =
                JsonFactory.newStringFieldDefinition("kty", FieldType.REGULAR);

        /**
         * JSON field containing the key's algorithm.
         */
        public static final JsonFieldDefinition<String> KEY_ALGORITHM =
                JsonFactory.newStringFieldDefinition("alg", FieldType.REGULAR);

        /**
         * JSON field containing the key's usage.
         */
        public static final JsonFieldDefinition<String> KEY_USAGE =
                JsonFactory.newStringFieldDefinition("use", FieldType.REGULAR);

        /**
         * JSON field containing the key's id.
         */
        public static final JsonFieldDefinition<String> KEY_ID =
                JsonFactory.newStringFieldDefinition("kid", FieldType.REGULAR);

        /**
         * JSON field containing the key's modulus.
         */
        public static final JsonFieldDefinition<String> KEY_MODULUS =
                JsonFactory.newStringFieldDefinition("n", FieldType.REGULAR);

        /**
         * JSON field containing the key's exponent.
         */
        public static final JsonFieldDefinition<String> KEY_EXPONENT =
                JsonFactory.newStringFieldDefinition("e", FieldType.REGULAR);

    }

}
