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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link JsonWebKey}.
 */
@Immutable
public final class ImmutableJsonWebKey implements JsonWebKey {

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String type;
    @Nullable private final String algorithm;
    @Nullable private final String usage;
    private final String id;
    @Nullable private final BigInteger modulus;
    @Nullable private final BigInteger exponent;
    @Nullable private final BigInteger xCoordinate;
    @Nullable private final BigInteger yCoordinate;
    @Nullable private final String curveType;

    private ImmutableJsonWebKey(final String type,
            @Nullable final String algorithm,
            @Nullable final String usage,
            final String id,
            @Nullable final BigInteger modulus,
            @Nullable final BigInteger exponent,
            @Nullable final BigInteger xCoordinate,
            @Nullable final BigInteger yCoordinate,
            @Nullable final String curveType) {

        this.type = type;
        this.algorithm = algorithm;
        this.usage = usage;
        this.id = id;
        this.modulus = modulus;
        this.exponent = exponent;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.curveType = curveType;
    }

    /**
     * Returns a new {@code JsonWebKey} for the given {@code type}, {@code algorithm}, {@code usage}, {@code id},
     * {@code modulus} and {@code exponent}.
     *
     * @param type the type of the JWK.
     * @param algorithm the algorithm of the JWK or {@code null}.
     * @param usage the usage of the JWK or {@code null}.
     * @param id the ID of the JWK.
     * @param modulus the optional modulus of the JWK.
     * @param exponent the optional exponent of the JWK.
     * @param xCoordinate the optional EC X coordinate.
     * @param yCoordinate the optional EC Y coordinate.
     * @param curveType the optional type of the used curve.
     * @return the JsonWebKey.
     * @throws NullPointerException if any argument but {@code algorithm} or {@code usage} is {@code null}.
     * @throws IllegalArgumentException if any {@code String} argument is empty.
     */
    public static JsonWebKey of(final String type,
            @Nullable final String algorithm,
            @Nullable final String usage,
            final String id,
            @Nullable final BigInteger modulus,
            @Nullable final BigInteger exponent,
            @Nullable final BigInteger xCoordinate,
            @Nullable final BigInteger yCoordinate,
            @Nullable final String curveType) {

        argumentNotEmpty(type);
        argumentNotEmpty(id);

        return new ImmutableJsonWebKey(type, algorithm, usage, id, modulus, exponent, xCoordinate, yCoordinate,
                curveType);
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

        final Optional<BigInteger> modulus = getOptionalValueFromJson(JsonFields.KEY_MODULUS, jsonObject);
        final Optional<BigInteger> exponent = getOptionalValueFromJson(JsonFields.KEY_EXPONENT, jsonObject);
        final Optional<BigInteger> xCoordinate = getOptionalValueFromJson(JsonFields.KEY_X_COORDINATE, jsonObject);
        final Optional<BigInteger> yCoordinate = getOptionalValueFromJson(JsonFields.KEY_Y_COORDINATE, jsonObject);
        final Optional<String> curveType = jsonObject.getValue(JsonFields.KEY_CURVE);


        return of(type, algorithm, usage, id, modulus.orElse(null), exponent.orElse(null),
                xCoordinate.orElse(null), yCoordinate.orElse(null), curveType.orElse(null));
    }

    private static Optional<BigInteger> getOptionalValueFromJson(final JsonFieldDefinition<String> field,
            final JsonObject jsonObject) {

        return jsonObject.getValue(field)
                .map(string -> string.getBytes(StandardCharsets.UTF_8))
                .map(DECODER::decode)
                .map(bytes -> new BigInteger(1, bytes));
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
    public Optional<BigInteger> getModulus() {
        return Optional.ofNullable(modulus);
    }

    @Override
    public Optional<BigInteger> getExponent() {
        return Optional.ofNullable(exponent);
    }

    @Override
    public Optional<BigInteger> getXCoordinate() {
        return Optional.ofNullable(xCoordinate);
    }

    @Override
    public Optional<BigInteger> getYCoordinate() {
        return Optional.ofNullable(yCoordinate);
    }

    @Override
    public Optional<String> getCurveType() {
        return Optional.ofNullable(curveType);
    }

    @Override
    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonWebKey that = (ImmutableJsonWebKey) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(usage, that.usage) &&
                Objects.equals(id, that.id) &&
                Objects.equals(modulus, that.modulus) &&
                Objects.equals(exponent, that.exponent) &&
                Objects.equals(xCoordinate, that.xCoordinate) &&
                Objects.equals(yCoordinate, that.yCoordinate) &&
                Objects.equals(curveType, that.curveType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, algorithm, usage, id, modulus, exponent, xCoordinate, yCoordinate, curveType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "type=" + type +
                ", algorithm=" + algorithm +
                ", usage=" + usage +
                ", id=" + id +
                ", modulus=" + modulus +
                ", exponent=" + exponent +
                ", xCoordinate=" + xCoordinate +
                ", yCoordinate=" + yCoordinate +
                ", curveType=" + curveType +
                ']';
    }

}
