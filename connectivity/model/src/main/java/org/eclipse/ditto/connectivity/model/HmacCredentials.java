/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Credentials containing information to sign HTTP requests with HMAC.
 *
 * @since 2.1.0
 */
@Immutable
public final class HmacCredentials implements Credentials {

    /**
     * Credential type name.
     */
    public static final String TYPE = "hmac";

    private final String algorithm;
    private final JsonObject parameters;

    private HmacCredentials(final String algorithm, final JsonObject parameters) {
        this.algorithm = checkNotNull(algorithm, "algorithm");
        this.parameters = checkNotNull(parameters, "parameters");
    }

    public static HmacCredentials of(final String algorithm, final JsonObject parameters) {
        return new HmacCredentials(algorithm, parameters);
    }

    @Override
    public <T> T accept(final CredentialsVisitor<T> visitor) {
        return visitor.hmac(this);
    }

    /**
     * @return the signing algorithm.
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * @return the parameters.
     */
    public JsonObject getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HmacCredentials that = (HmacCredentials) o;
        return Objects.equals(algorithm, that.algorithm) &&
                Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, parameters);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "algorithm=" + algorithm +
                ", parameters=" + parameters +
                "]";
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(Credentials.JsonFields.TYPE, TYPE);
        jsonObjectBuilder.set(JsonFields.ALGORITHM, algorithm);
        jsonObjectBuilder.set(JsonFields.PARAMETERS, parameters);
        return jsonObjectBuilder.build();
    }

    static HmacCredentials fromJson(final JsonObject jsonObject) {
        return new HmacCredentials(jsonObject.getValueOrThrow(JsonFields.ALGORITHM),
                jsonObject.getValueOrThrow(JsonFields.PARAMETERS));
    }

    /**
     * JSON field definitions.
     */
    public static final class JsonFields extends Credentials.JsonFields {

        /**
         * JSON field definition of the request-signing algorithm name.
         */
        public static final JsonFieldDefinition<String> ALGORITHM = JsonFieldDefinition.ofString("algorithm");

        /**
         * JSON field definition of parameters for the request-signing algorithm.
         */
        public static final JsonFieldDefinition<JsonObject> PARAMETERS = JsonFieldDefinition.ofJsonObject("parameters");
    }
}
