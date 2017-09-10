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

import static org.eclipse.ditto.json.JsonFactory.newFieldDefinition;

import java.math.BigInteger;
import java.util.Optional;

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
    final class JsonFields {

        /**
         * JSON field containing the key's type.
         */
        public static final JsonFieldDefinition KEY_TYPE = newFieldDefinition("kty", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the key's algorithm.
         */
        public static final JsonFieldDefinition KEY_ALGORITHM =
                newFieldDefinition("alg", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the key's usage.
         */
        public static final JsonFieldDefinition KEY_USAGE = newFieldDefinition("use", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the key's id.
         */
        public static final JsonFieldDefinition KEY_ID = newFieldDefinition("kid", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the key's modulus.
         */
        public static final JsonFieldDefinition KEY_MODULUS = newFieldDefinition("n", String.class, FieldType.REGULAR);

        /**
         * JSON field containing the key's exponent.
         */
        public static final JsonFieldDefinition KEY_EXPONENT = newFieldDefinition("e", String.class, FieldType.REGULAR);
    }

}
