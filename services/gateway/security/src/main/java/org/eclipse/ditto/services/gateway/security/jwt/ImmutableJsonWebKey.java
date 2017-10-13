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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link JsonWebKey}.
 */
@Immutable
public final class ImmutableJsonWebKey implements JsonWebKey {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String type;
    private final String algorithm;
    private final String usage;
    private final String id;
    private final BigInteger modulus;
    private final BigInteger exponent;

    private ImmutableJsonWebKey(final String type,
            final String algorithm,
            final String usage,
            final String id,
            final BigInteger modulus,
            final BigInteger exponent) {

        this.type = type;
        this.algorithm = algorithm;
        this.usage = usage;
        this.id = id;
        this.modulus = modulus;
        this.exponent = exponent;
    }

    /**
     * Returns a new {@code JsonWebKey} for the given {@code type}, {@code algorithm}, {@code usage}, {@code id},
     * {@code modulus} and {@code exponent}.
     *
     * @param type the type of the JWK.
     * @param algorithm the algorithm of the JWK.
     * @param usage the usage of the JWK.
     * @param id the id of the JWK.
     * @param modulus the modulus of the JWK.
     * @param exponent the exponent of the JWK.
     * @return the JsonWebKey.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any {@code String} argument is empty.
     */
    public static JsonWebKey of(final String type,
            final String algorithm,
            final String usage,
            final String id,
            final BigInteger modulus,
            final BigInteger exponent) {

        argumentNotEmpty(type);
        argumentNotEmpty(id);
        argumentNotNull(modulus);
        argumentNotNull(exponent);

        return new ImmutableJsonWebKey(type, algorithm, usage, id, modulus, exponent);
    }

    /**
     * Returns a new {@code JsonWebKey} for the given {@code jsonString}.
     *
     * @param jsonString the string containing the JSON Web Key.
     * @return the JsonWebKey.
     */
    public static JsonWebKey fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Returns a new {@code JsonWebKey} for the given {@code jsonObject}.
     *
     * @param jsonObject the object containing the JSON Web Key.
     * @return the JsonWebKey.
     * @throws JsonMissingFieldException if any {@code JsonField} is missing.
     */
    public static JsonWebKey fromJson(final JsonObject jsonObject) {
        final String type = jsonObject.getValueOrThrow(JsonFields.KEY_TYPE);
        final String algorithm = jsonObject.getValue(JsonFields.KEY_ALGORITHM).orElse(null);
        final String usage = jsonObject.getValue(JsonFields.KEY_USAGE).orElse(null);
        final String id = jsonObject.getValueOrThrow(JsonFields.KEY_ID);
        final BigInteger modulus = jsonObject.getValue(JsonFields.KEY_MODULUS)
                .map(string -> string.getBytes(StandardCharsets.UTF_8))
                .map(DECODER::decode)
                .map(bytes -> new BigInteger(1, bytes))
                .orElseThrow(() -> new JsonMissingFieldException(JsonFields.KEY_MODULUS.getPointer()));
        final BigInteger exponent = jsonObject.getValue(JsonFields.KEY_EXPONENT)
                .map(string -> string.getBytes(StandardCharsets.UTF_8))
                .map(DECODER::decode)
                .map(bytes -> new BigInteger(1, bytes))
                .orElseThrow(() -> new JsonMissingFieldException(JsonFields.KEY_EXPONENT.getPointer()));

        return of(type, algorithm, usage, id, modulus, exponent);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Optional<String> getAlgorithm() {
        return Optional.ofNullable(algorithm);
    }

    @Override
    public Optional<String> getUsage() {
        return Optional.ofNullable(usage);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public BigInteger getModulus() {
        return modulus;
    }

    @Override
    public BigInteger getExponent() {
        return exponent;
    }

    @Override
    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067", "OverlyComplexMethod"})
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonWebKey that = (ImmutableJsonWebKey) o;
        return Objects.equals(type, that.type) && Objects.equals(algorithm, that.algorithm)
                && Objects.equals(usage, that.usage) && Objects.equals(id, that.id) &&
                Objects.equals(modulus, that.modulus)
                && Objects.equals(exponent, that.exponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, algorithm, usage, id, modulus, exponent);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "type=" + type + ", algorithm=" + algorithm + ", usage=" + usage
                + ", id=" + id + ", modulus=" + modulus + ", exponent=" + exponent + ']';
    }

}
