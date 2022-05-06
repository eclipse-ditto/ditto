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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.time.Duration;
import java.util.List;

import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorSystem;

/**
 * Creator of the signing process {@code AWS4-HMAC-SHA256}.
 */
public final class AwsRequestSigningFactory implements HttpRequestSigningFactory {

    /**
     * Token timeout to evaluate the body of outgoing requests, which should take very little time as it does not
     * depend on IO.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Which header to sign by default.
     */
    private static final List<String> DEFAULT_CANONICAL_HEADERS = List.of("host");

    @Override
    public HttpRequestSigning create(final ActorSystem actorSystem, final HmacCredentials credentials) {
        final JsonObject parameters = credentials.getParameters();
        final String region = parameters.getValueOrThrow(JsonFields.REGION);
        final String service = parameters.getValueOrThrow(JsonFields.SERVICE);
        final String accessKey = parameters.getValueOrThrow(JsonFields.ACCESS_KEY);
        final String secretKey = parameters.getValueOrThrow(JsonFields.SECRET_KEY);
        final boolean doubleEncode = parameters.getValue(JsonFields.DOUBLE_ENCODE).orElse(true);
        final List<String> canonicalHeaders = parameters.getValue(JsonFields.CANONICAL_HEADERS)
                .map(array -> array.stream().map(JsonValue::asString).toList())
                .orElse(DEFAULT_CANONICAL_HEADERS);
        final var xAmzContentSha256 = parameters.getValue(JsonFields.X_AMZ_CONTENT_SHA256)
                .map(AwsRequestSigning.XAmzContentSha256::forName)
                .orElse(AwsRequestSigning.XAmzContentSha256.EXCLUDED);
        return new AwsRequestSigning(actorSystem, canonicalHeaders, region, service, accessKey, secretKey, doubleEncode,
                TIMEOUT, xAmzContentSha256);
    }

    /**
     * JSON fields of algorithm parameters.
     */
    public static final class JsonFields {

        /**
         * Obligatory: The AWS region of the signed requests.
         */
        public static final JsonFieldDefinition<String> REGION = JsonFieldDefinition.ofString("region");

        /**
         * Obligatory: The service for which the signed requests are intended.
         */
        public static final JsonFieldDefinition<String> SERVICE = JsonFieldDefinition.ofString("service");

        /**
         * Obligatory: Access key to sign requests with.
         */
        public static final JsonFieldDefinition<String> ACCESS_KEY = JsonFieldDefinition.ofString("accessKey");

        /**
         * Obligatory: Secret key to sign requests with.
         */
        public static final JsonFieldDefinition<String> SECRET_KEY = JsonFieldDefinition.ofString("secretKey");

        /**
         * Optional: Whether to double-encode and normalize path segments. True by default. Set to false for S3.
         */
        public static final JsonFieldDefinition<Boolean> DOUBLE_ENCODE = JsonFieldDefinition.ofBoolean("doubleEncode");

        /**
         * Optional: Which headers to sign. They differ for each AWS service. By default only "host" is signed.
         */
        public static final JsonFieldDefinition<JsonArray> CANONICAL_HEADERS =
                JsonFieldDefinition.ofJsonArray("canonicalHeaders");

        public static final JsonFieldDefinition<String> X_AMZ_CONTENT_SHA256 =
                JsonFieldDefinition.ofString("xAmzContentSha256");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
