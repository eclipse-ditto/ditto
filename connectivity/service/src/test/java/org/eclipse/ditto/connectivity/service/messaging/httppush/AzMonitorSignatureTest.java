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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.Test;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;

/**
 * Test cases for Azure Monitor request signing.
 */
public final class AzMonitorSignatureTest {

    private static final String WORKSPACE_ID = "xxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";

    private static final String SHARED_KEY = "SGFsbG8gV2VsdCEgSXN0IGRhcyBhbG";

    private static final Instant X_MS_DATE = Instant.parse("2021-01-01T00:00:00Z");

    @Test
    public void testRequestSignature() {
        final HttpRequest requestToSign = getSampleHttpRequest();
        final String expectedXMsDate = "Fri, 01 Jan 2021 00:00:00 GMT";
        final String expectedStringToSign = "POST\n" +
                "24\n" +
                "application/json\n" +
                "x-ms-date:" + expectedXMsDate + "\n" +
                "/api/logs";

        final String stringToSign = getStringToSign(requestToSign);
        assertThat(stringToSign).describedAs("stringToSign").isEqualTo(expectedStringToSign);

        final HttpRequest expectedSignedRequest = getSampleHttpRequest()
                .addHeader(HttpHeader.parse("x-ms-date", expectedXMsDate))
                .addCredentials(HttpCredentials.create("SharedKey",
                        "xxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:026ydk2bCals83UTzd6OoaG7fqKR2NQV+IUuAJcgG8Q="));
        final HttpRequest signedRequest = signRequest(requestToSign);
        assertThat(signedRequest).describedAs("signedRequest").isEqualTo(expectedSignedRequest);
    }

    private static HttpRequest getSampleHttpRequest() {
        final var url =
                String.format("https://%s.ods.opinsights.azure.com/api/logs?api-version=2016-04-01", WORKSPACE_ID);
        final var requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, "{\"name\":\"log\",\"id\":1}");
        return HttpRequest.POST(url)
                .addHeader(HttpHeader.parse("Log-Type", "log_type_1123"))
                .addHeader(HttpHeader.parse("Connection", "keep-alive"))
                .addHeader(HttpHeader.parse("correlation-id", "qwerty"))
                .withEntity(requestEntity);
    }

    private static String getStringToSign(final HttpRequest httpRequest) {
        throw new UnsupportedOperationException("TODO " + X_MS_DATE);
    }

    private static HttpRequest signRequest(final HttpRequest originalRequest) {
        throw new UnsupportedOperationException("TODO " + SHARED_KEY + X_MS_DATE);
    }

}
