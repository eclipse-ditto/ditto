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
package org.eclipse.ditto.jwt.model;

import java.math.BigInteger;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;

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
     * @since 3.0.0
     */
    Optional<BigInteger> getModulus();

    /**
     * Returns the exponent parameter.
     *
     * @return the exponent.
     * @since 3.0.0
     */
    Optional<BigInteger> getExponent();

    /**
     * Returns the EC X coordinate.
     *
     * @return the EC X coordinate.
     * @since 3.0.0
     */
    Optional<BigInteger> getXCoordinate();

    /**
     * Returns the EC Y coordinate.
     *
     * @return the EC Y coordinate.
     * @since 3.0.0
     */
    Optional<BigInteger> getYCoordinate();

    /**
     * Returns the curve type.
     *
     * @return the curve type.
     * @since 3.0.0
     */
    Optional<String> getCurveType();

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

        /**
         * JSON field containing the key's EC X coordinate.
         * @since 3.0.0
         */
        public static final JsonFieldDefinition<String> KEY_X_COORDINATE =
                JsonFactory.newStringFieldDefinition("x", FieldType.REGULAR);

        /**
         * JSON field containing the key's EC Y coordinate.
         * @since 3.0.0
         */
        public static final JsonFieldDefinition<String> KEY_Y_COORDINATE =
                JsonFactory.newStringFieldDefinition("y", FieldType.REGULAR);

        /**
         * JSON field containing the key's EC curve.
         * @since 3.0.0
         */
        public static final JsonFieldDefinition<String> KEY_CURVE =
                JsonFactory.newStringFieldDefinition("crv", FieldType.REGULAR);

        private JsonFields() {
            throw new AssertionError();
        }
    }

}
