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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.stream.javadsl.Sink;
import akka.testkit.javadsl.TestKit;

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

    private final ActorSystem actorSystem = ActorSystem.create();

    @After
    public void shutdown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void testCanonicalQuery() {
        final Uri uri = Uri.create("https://www.example.com?Action=ListUsers&" +
                "X-Amz-Date=20150830T123600Z&" +
                "X-Amz-Credential=AKIDEXAMPLE/20150830/us-east-1/iam/aws4_request&" +
                "X-Amz-Algorithm=AWS4-HMAC-SHA256&" +
                "X-Amz-SignedHeaders=content-type;host;x-amz-date&" +
                "Version=2010-05-08&" +
                "Order-by-Value=2&" +
                "Order-by-Value=3&" +
                "Order-by-Value=1&" +
                "Double-Encode-Equality=a=b");

        final String expectedCanonicalQuery = "Action=ListUsers&" +
                "Double-Encode-Equality=a%253Db&" +
                "Order-by-Value=1&" +
                "Order-by-Value=2&" +
                "Order-by-Value=3&" +
                "Version=2010-05-08&" +
                "X-Amz-Algorithm=AWS4-HMAC-SHA256&" +
                "X-Amz-Credential=AKIDEXAMPLE%2F20150830%2Fus-east-1%2Fiam%2Faws4_request&" +
                "X-Amz-Date=20150830T123600Z&" +
                "X-Amz-SignedHeaders=content-type%3Bhost%3Bx-amz-date";

        final String canonicalQuery = AwsRequestSigning.getCanonicalQuery(uri.query());

        assertThat(canonicalQuery).isEqualTo(expectedCanonicalQuery);
    }

    @Test
    public void testCanonicalHeaders() {
        // lower-case charset does not work; Akka HTTP always sends the charset in upper case.
        final ContentType contentType = ContentTypes.parse("application/x-www-form-urlencoded; charset=UTF-8");
        final RequestEntity body = HttpEntities.create(contentType, new byte[0]);
        final List<HttpHeader> headesInSequence = List.of(
                HttpHeader.parse("Host", "iam.amazonaws.com"),
                HttpHeader.parse("My-header1", "    a   b   c  "),
                HttpHeader.parse("My-Header2", "    \"a   b   c\"  "),
                HttpHeader.parse("My-header2", "def")
        );

        final HttpRequest request = HttpRequest.GET("https://iam.amazonaws.com")
                .withEntity(body)
                // can't use .addHeader because it _prepends_ the header to the list
                .withHeaders(headesInSequence);

        final Collection<String> canonicalHeaderNames = AwsRequestSigning.toDeduplicatedSortedLowerCase(
                List.of("Host", "My-header1", "X-Amz-Date", "my-headER2", "CONTENT-TYPE", "nonexistent-header"));

        final String expectedCanonicalHeaders =
                "content-type:application/x-www-form-urlencoded; charset=UTF-8\n" +
                        "host:iam.amazonaws.com\n" +
                        "my-header1:a b c\n" +
                        "my-header2:\"a b c\",def\n" +
                        "nonexistent-header:\n" +
                        "x-amz-date:20150830T123600Z\n";

        final String canonicalHeaders =
                AwsRequestSigning.getCanonicalHeaders(request, canonicalHeaderNames,
                        Instant.parse("2015-08-30T12:36:00Z"));

        assertThat(canonicalHeaders).isEqualTo(expectedCanonicalHeaders);
    }

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
    public void testS3CanonicalUri() {
        final Uri uri = Uri.create("https://www.example.com/my-object//example//photo.user");
        assertThat(AwsRequestSigning.getCanonicalUri(uri, false))
                .isEqualTo("/my-object//example//photo.user");
    }

    @Test
    public void testNonS3CanonicalUrl() {
        final Uri uri = Uri.create("https://www.example.com///documents%20and%20settings//////");
        assertThat(AwsRequestSigning.getCanonicalUri(uri, true))
                .isEqualTo("/documents%2520and%2520settings/");
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

    private HttpRequest signRequest(final HttpRequest originalRequest) {
        return new AwsRequestSigning(actorSystem, List.of("host", "x-amz-date"), REGION_NAME, SERVICE_NAME, ACCESS_KEY,
                SECRET_KEY, true, Duration.ofSeconds(10))
                .sign(originalRequest, X_AMZ_DATE)
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    private String getStringToSign(final HttpRequest httpRequest) {
        return new AwsRequestSigning(actorSystem, List.of("host", "x-amz-date"), REGION_NAME, SERVICE_NAME, ACCESS_KEY,
                SECRET_KEY, true, Duration.ofSeconds(10))
                .getStringToSign(httpRequest, X_AMZ_DATE, true);
    }

    private String getCanonicalRequest(final HttpRequest httpRequest) {
        return new AwsRequestSigning(actorSystem, List.of("host", "x-amz-date"), REGION_NAME, SERVICE_NAME, ACCESS_KEY,
                SECRET_KEY, true, Duration.ofSeconds(10))
                .getCanonicalRequest(httpRequest, X_AMZ_DATE, true);
    }

    private static String getKSecret() {
        return toHex(AwsRequestSigning.getKSecret(SECRET_KEY));
    }

    private static String getKDate() {
        return toHex(AwsRequestSigning.getKDate(Hex.decode(getKSecret()), X_AMZ_DATE));
    }

    private static String getKRegion() {
        return toHex(AwsRequestSigning.getKRegion(Hex.decode(getKDate()), REGION_NAME));
    }

    private static String getKService() {
        return toHex(AwsRequestSigning.getKService(Hex.decode(getKRegion()), SERVICE_NAME));
    }

    private static String getKSigning() {
        return toHex(AwsRequestSigning.getKSigning(Hex.decode(getKService())));
    }

    private static String toHex(final byte[] bytes) {
        return Hex.toHexString(bytes);
    }
}
