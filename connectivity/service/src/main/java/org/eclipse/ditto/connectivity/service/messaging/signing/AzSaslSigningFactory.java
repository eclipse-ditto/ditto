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
package org.eclipse.ditto.connectivity.service.messaging.signing;

import java.time.Duration;

import org.eclipse.ditto.base.model.common.DittoDuration;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpConnectionSigning;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpConnectionSigningFactory;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpRequestSigning;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpRequestSigningFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;

/**
 * Creator of the signing process for Azure SASL.
 */
public final class AzSaslSigningFactory implements HttpRequestSigningFactory, AmqpConnectionSigningFactory {

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);

    @Override
    public HttpRequestSigning create(final ActorSystem actorSystem, final HmacCredentials credentials) {
        return createSigning(credentials);
    }

    @Override
    public AmqpConnectionSigning createAmqpConnectionSigning(final HmacCredentials credentials) {
        return createSigning(credentials);
    }

    private AzSaslSigning createSigning(final HmacCredentials credentials) {
        final JsonObject parameters = credentials.getParameters();
        final String sharedKeyName = parameters.getValueOrThrow(JsonFields.SHARED_KEY_NAME);
        final String sharedKey = parameters.getValueOrThrow(JsonFields.SHARED_KEY);
        final Duration ttl = parameters.getValue(JsonFields.TTL).map(this::parseDuration).orElse(DEFAULT_TTL);
        final String endpoint = parameters.getValueOrThrow(JsonFields.ENDPOINT);
        return AzSaslSigning.of(sharedKeyName, sharedKey, ttl, endpoint);
    }

    private Duration parseDuration(final String string) {
        return DittoDuration.parseDuration(string).getDuration();
    }

    /**
     * JSON fields of algorithm parameters.
     */
    public static final class JsonFields {

        /**
         * Obligatory: The shared key name.
         */
        public static final JsonFieldDefinition<String> SHARED_KEY_NAME = JsonFieldDefinition.ofString("sharedKeyName");

        /**
         * Obligatory: The shared key.
         */
        public static final JsonFieldDefinition<String> SHARED_KEY = JsonFieldDefinition.ofString("sharedKey");

        /**
         * Obligatory: Value of the field {@code sr} to include in the signature.
         */
        public static final JsonFieldDefinition<String> ENDPOINT = JsonFieldDefinition.ofString("endpoint");

        /**
         * Optional: How long should tokens remain valid after creation. Default to 1 week.
         */
        public static final JsonFieldDefinition<String> TTL = JsonFieldDefinition.ofString("ttl");

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
