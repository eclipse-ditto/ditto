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

import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;

/**
 * Creator of the signing process for Azure Monitor Data Collector.
 */
public final class AzMonitorRequestSigningFactory implements HttpRequestSigningFactory {

    /**
     * Token timeout to evaluate the body of outgoing requests, which should take very little time as it does not
     * depend on IO.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public HttpRequestSigning create(final ActorSystem actorSystem, final HmacCredentials credentials) {
        final JsonObject parameters = credentials.getParameters();
        final String workspaceId = parameters.getValueOrThrow(JsonFields.WORKSPACE_ID);
        final String sharedKey = parameters.getValueOrThrow(JsonFields.SHARED_KEY);
        return AzMonitorRequestSigning.of(actorSystem, workspaceId, sharedKey, TIMEOUT);
    }

    /**
     * JSON fields of algorithm parameters.
     */
    public static final class JsonFields {

        /**
         * Obligatory: The Azure workspace ID of the signed requests.
         */
        public static final JsonFieldDefinition<String> WORKSPACE_ID = JsonFieldDefinition.ofString("workspaceId");

        /**
         * Obligatory: The shared key with which to sign requests.
         */
        public static final JsonFieldDefinition<String> SHARED_KEY = JsonFieldDefinition.ofString("sharedKey");

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
