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
import java.util.Map;

import org.junit.Test;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.headers.HttpCredentials;

/**
 * Test cases for AWS request signing.
 */
public final class AwsRequestSigningTest {

    private static final String ACCESS_KEY = "MyAwesomeAccessKey";

    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";

    private static final Instant X_AMZ_DATE = Instant.parse("2012-02-15T00:00:00Z");

    private static final String REGION_NAME = "us-east-1";

    private static final String SERVICE_NAME = "iam";

    private static final String BODY = "The quick brown fox jumped over the lazy dog";

    @Test
    public void testRequestSignature() {
        final HttpRequest requestToSign = getSampleHttpRequest();
        final String expectedXAmzDate = "20120215T000000Z";
        final String expectedCanonicalRequest = "POST\n" +
                "/p/a/t/h\n" +
                "parameter=value\n" +
                "host:www.example.com\n" +
                "x-amz-date:" + expectedXAmzDate + "\n" +
                "\n" +
                "host;x-amz-date\n" +
                "7d38b5cd25a2baf85ad3bb5b9311383e671a8a142eb302b324d4a5fba8748c69";
        assertThat(getCanonicalRequest(requestToSign))
                .describedAs("canonicalRequest")
                .isEqualTo(expectedCanonicalRequest);

        final String expectedStringToSign = "AWS4-HMAC-SHA256\n" +
                "20120215T000000Z\n" +
                "20120215/us-east-1/iam/aws4_request\n" +
                "0dc3c125fe9106dfb0bf8c1f3c9552f9a56c8e5226659292303bf45232253f26";

        final String stringToSign = getStringToSign(requestToSign);
        assertThat(stringToSign).describedAs("stringToSign").isEqualTo(expectedStringToSign);

        assertThat(getKSecret()).describedAs("kSecret")
                .isEqualTo("41575334774a616c725855746e46454d492f4b374d44454e472b62507852666943594558414d504c454b4559");

        assertThat(getKDate()).describedAs("kDate")
                .isEqualTo("969fbb94feb542b71ede6f87fe4d5fa29c789342b0f407474670f0c2489e0a0d");

        assertThat(getKRegion()).describedAs("kRegion")
                .isEqualTo("69daa0209cd9c5ff5c8ced464a696fd4252e981430b10e3d3fd8e2f197d7a70c");

        assertThat(getKService()).describedAs("kService")
                .isEqualTo("f72cfd46f26bc4643f06a11eabb6c0ba18780c19a8da0c31ace671265e3c87fa");

        assertThat(getKSigning()).describedAs("kSigning")
                .isEqualTo("f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d");

        final Map<String, String> authorizationParams = Map.of(
                "Credential", "MyAwesomeAccessKey/20120215/us-east-1/iam/aws4_request",
                "SignedHeaders", "host;x-amz-date",
                "Signature", "eda3fcc970a1d0cd3a3c3b8e7c80e876eec16d3b44459ce3e48fffd8226e4dca"
        );

        final HttpRequest expectedSignedRequest = getSampleHttpRequest()
                .addHeader(HttpHeader.parse("x-amz-date", expectedXAmzDate))
                .addCredentials(HttpCredentials.create("AWS4-HMAC-SHA256", "", authorizationParams));
        final HttpRequest signedRequest = signRequest(requestToSign);
        assertThat(signedRequest).describedAs("signedRequest").isEqualTo(expectedSignedRequest);
    }

    @Test
    public void testS3CanonicalUrl() {
        throw new AssertionError("TODO");
    }

    @Test
    public void testNonS3CanonicalUrl() {
        throw new AssertionError("TODO");
    }

    private static HttpRequest getSampleHttpRequest() {
        final var url = "https://www.example.com/p/a/t/h?parameter=value";
        final var requestEntity =
                HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, BODY);
        return HttpRequest.POST(url)
                .addHeader(HttpHeader.parse("Connection", "keep-alive"))
                .addHeader(HttpHeader.parse("correlation-id", "qwerty"))
                .withEntity(requestEntity);
    }

    private static String getStringToSign(final HttpRequest httpRequest) {
        throw new UnsupportedOperationException("TODO " + X_AMZ_DATE);
    }

    private static HttpRequest signRequest(final HttpRequest originalRequest) {
        throw new UnsupportedOperationException("TODO " + ACCESS_KEY + SECRET_KEY);
    }

    private static String getCanonicalRequest(final HttpRequest httpRequest) {
        throw new UnsupportedOperationException("TODO " + httpRequest);
    }

    private static String getKSecret() {
        throw new UnsupportedOperationException("TODO " + SECRET_KEY);
    }

    private static String getKDate() {
        throw new UnsupportedOperationException("TODO " + X_AMZ_DATE);
    }

    private static String getKRegion() {
        throw new UnsupportedOperationException("TODO " + REGION_NAME);
    }

    private static String getKService() {
        throw new UnsupportedOperationException("TODO " + SERVICE_NAME);
    }

    private static String getKSigning() {
        throw new UnsupportedOperationException("TODO aws4_request");
    }

}
