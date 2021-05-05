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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.Uri;

/**
 * Tests {@link org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter}.
 */
public class RequestLoggingFilterTest {

    private static final String TOKEN = UUID.randomUUID().toString();
    private static final String BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes());
    private static final String ACCESS_TOKEN = "access_token";

    private static final Set<String> TEST_PARAMETERS = Set.of(
            ACCESS_TOKEN + "=" + TOKEN,
            "foo=bar&" + ACCESS_TOKEN + "=" + TOKEN,
            ACCESS_TOKEN + "=" + TOKEN + "&foo=bar",
            "foo=bar&" + ACCESS_TOKEN + "=" + TOKEN + "&bar=foo",
            ACCESS_TOKEN + "=" + TOKEN + "&bar=foo&" + ACCESS_TOKEN + "=" + TOKEN
    );

    @Test
    public void filterAccessTokenParameterInUri() {
        TEST_PARAMETERS.forEach(this::filterAccessTokenParameterInUri);
    }

    private void filterAccessTokenParameterInUri(final String parameterString) {
        final Uri uri = Uri.create("http://localhost:8080/api/2/things?" + parameterString);
        assertThat(uri.query().get(ACCESS_TOKEN)).contains(TOKEN);
        final Uri filteredUri = RequestLoggingFilter.filterUri(uri);
        assertThat(filteredUri.query().get(ACCESS_TOKEN)).contains("***");
    }

    @Test
    public void filterUriReturnsSameInstance() {
        final Uri uri = Uri.create("http://localhost:8080/api/2/things?foo=bar");
        final Uri filteredUri = RequestLoggingFilter.filterUri(uri);
        assertThat(filteredUri).isSameAs(uri);
    }

    @Test
    public void filterRawUriReturnsSameInstance() {
        final String rawUri = Uri.create("http://localhost:8080/api/2/things?foo=bar").toString();
        final String filteredRawUri = RequestLoggingFilter.filterRawUri(rawUri);
        assertThat(filteredRawUri).isSameAs(rawUri);
    }

    @Test
    public void filterAccessTokenParameterInRawUri() {
        TEST_PARAMETERS.forEach(this::filterAccessTokenParameterInRawUri);
    }

    private void filterAccessTokenParameterInRawUri(final String parameterString) {
        final String rawUri = Uri.create("http://localhost:8080/api/2/things?" + parameterString).toString();
        assertThat(rawUri).contains("access_token=" + TOKEN);
        final String filteredRawUri = RequestLoggingFilter.filterRawUri(rawUri);
        assertThat(filteredRawUri).contains("access_token=***");
        assertThat(filteredRawUri).doesNotContain(TOKEN);
    }

    @Test
    public void filterAccessTokenParameterInHeaders() {
        filterAccessTokenParameterInHeaders("authorization");
        filterAccessTokenParameterInHeaders("Authorization");
        filterAccessTokenParameterInHeaders("AuThOrIzAtIoN");
    }

    private void filterAccessTokenParameterInHeaders(final String name) {
        final Iterable<HttpHeader> headers =
                Set.of(HttpHeader.parse("foo", "bar"),
                        HttpHeader.parse(name, BASIC_AUTH),
                        HttpHeader.parse("eclipse", "ditto"));

        final Iterable<HttpHeader> filteredHeaders = RequestLoggingFilter.filterHeaders(headers);

        assertThat(filteredHeaders).hasSize(3);
        assertThat(filteredHeaders).contains(HttpHeader.parse(name, "***"));
    }
}
