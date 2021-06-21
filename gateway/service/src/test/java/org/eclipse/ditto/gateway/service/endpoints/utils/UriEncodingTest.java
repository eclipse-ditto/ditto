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
package org.eclipse.ditto.gateway.service.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.base.service.UriEncoding.EncodingType.FORM_URL_ENCODED;
import static org.eclipse.ditto.base.service.UriEncoding.EncodingType.RFC3986;
import static org.junit.Assert.assertEquals;

import org.eclipse.ditto.base.service.UriEncoding;
import org.junit.Test;

import akka.http.javadsl.model.Uri;
import akka.http.scaladsl.model.IllegalUriException;

/**
 * Unit test for {@link org.eclipse.ditto.base.service.UriEncoding}.
 */
public final class UriEncodingTest {

    private static final String CHARS_UNSUPPORTED_RELAXED = " ä\"%";
    private static final String CHARS_UNSUPPORTED_STRICT = "\\[]{}|";
    private static final String CHARS_SUPPORTED = "!#$&'()*+,/:;=?@aZ0";

    private static final String MANY_DIFFERENT_CHARS = "!\"#$%&'()*+,/:;=?@[\\]{|} äaZ0";
    private static final String MANY_DIFFERENT_CHARS_QUERY_PARAM_ENCODED_RFC3986 =
            "!%22%23$%25%26'()*%2B,/:;%3D?@%5B%5C%5D%7B%7C%7D%20%C3%A4aZ0";

    @Test
    public void specialCharsWithoutEncodingRelaxed() {
        Uri.create("http://" + CHARS_SUPPORTED + CHARS_UNSUPPORTED_STRICT, Uri.RELAXED);

        assertThatExceptionOfType(IllegalUriException.class)
                .isThrownBy(() -> Uri.create("http://" + CHARS_UNSUPPORTED_RELAXED, Uri.RELAXED))
                .withNoCause();
    }

    @Test
    public void specialCharsWithoutEncodingStrict() {
        Uri.create("http://" + CHARS_SUPPORTED, Uri.STRICT);

        assertThatExceptionOfType(IllegalUriException.class)
                .isThrownBy(() -> Uri.create("http://" + CHARS_UNSUPPORTED_STRICT, Uri.STRICT))
                .withNoCause();
    }

    @Test
    public void specialCharsWithEncoding() {
        final String encodedChars =
                UriEncoding.encodePath(CHARS_SUPPORTED + CHARS_UNSUPPORTED_STRICT + CHARS_UNSUPPORTED_RELAXED);
        Uri.create("http://" + encodedChars);
    }

    @Test
    public void encodePath() {
        assertEquals("foo", UriEncoding.encodePath("foo"));
        assertEquals("/top/sub", UriEncoding.encodePath("/top/sub"));
        assertEquals("!%22%23$%25&'()*+,/:;=%3F@%5B%5C%5D%7B%7C%7D%20%C3%A4aZ0",
                UriEncoding.encodePath(MANY_DIFFERENT_CHARS));
    }

    @Test
    public void encodePathSegment() {
        assertEquals("foo", UriEncoding.encodePathSegment("foo"));
        assertEquals("%2Ftop%2Fsub", UriEncoding.encodePathSegment("/top/sub"));
        assertEquals("!%22%23$%25&'()*+,%2F:;=%3F@%5B%5C%5D%7B%7C%7D%20%C3%A4aZ0",
                UriEncoding.encodePathSegment(MANY_DIFFERENT_CHARS));
    }

    @Test
    public void encodeQueryRfc3986() {
        assertEquals("foo", UriEncoding.encodeQuery("foo", RFC3986));
        assertEquals("foo=bar/%2B&baz=1", UriEncoding.encodeQuery("foo=bar/+&baz=1", RFC3986));
        assertEquals("!%22%23$%25&'()*%2B,/:;=?@%5B%5C%5D%7B%7C%7D%20%C3%A4aZ0",
                UriEncoding.encodeQuery(MANY_DIFFERENT_CHARS, RFC3986));
    }

    @Test
    public void encodeQueryParamRfc3986() {
        assertEquals("foo", UriEncoding.encodeQueryParam("foo", RFC3986));
        assertEquals("foo%3Dbar/%2B%26baz%3D1", UriEncoding.encodeQueryParam("foo=bar/+&baz=1", RFC3986));
        assertEquals(MANY_DIFFERENT_CHARS_QUERY_PARAM_ENCODED_RFC3986,
                UriEncoding.encodeQueryParam(MANY_DIFFERENT_CHARS, RFC3986));
    }

    @Test
    public void encodeQueryFormUrlEncoded() {
        assertEquals("foo", UriEncoding.encodeQuery("foo", FORM_URL_ENCODED));
        assertEquals("foo=bar%2F%2B&baz=1", UriEncoding.encodeQuery("foo=bar/+&baz=1", FORM_URL_ENCODED));
        assertEquals("%21%22%23%24%25&%27%28%29*%2B%2C%2F%3A%3B=%3F%40%5B%5C%5D%7B%7C%7D+%C3%A4aZ0",
                UriEncoding.encodeQuery(MANY_DIFFERENT_CHARS, FORM_URL_ENCODED));
    }

    @Test
    public void encodeQueryParamFormUrlEncoded() {
        assertEquals("foo", UriEncoding.encodeQueryParam("foo", FORM_URL_ENCODED));
        assertEquals("foo%3Dbar%2F%2B%26baz%3D1", UriEncoding.encodeQueryParam("foo=bar/+&baz=1", FORM_URL_ENCODED));
        assertEquals("%21%22%23%24%25%26%27%28%29*%2B%2C%2F%3A%3B%3D%3F%40%5B%5C%5D%7B%7C%7D+%C3%A4aZ0",
                UriEncoding.encodeQueryParam(MANY_DIFFERENT_CHARS, FORM_URL_ENCODED));
    }

    @Test
    public void decodeRfc3986() {
        assertEquals("", UriEncoding.decode("", RFC3986));
        assertEquals("foo", UriEncoding.decode("foo", RFC3986));
        assertEquals("with space", UriEncoding.decode("with%20space", RFC3986));
        assertEquals("with+PlusEncoded", UriEncoding.decode("with%2bPlusEncoded", RFC3986));
        assertEquals("with+PlusNotEncoded", UriEncoding.decode("with+PlusNotEncoded", RFC3986));
        assertEquals(MANY_DIFFERENT_CHARS,
                UriEncoding.decode(MANY_DIFFERENT_CHARS_QUERY_PARAM_ENCODED_RFC3986, RFC3986));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeRfc3986InvalidSequence() {
        UriEncoding.decode("foo%2", RFC3986);
    }

    @Test
    public void decodeFormUrlEncoded() {
        assertEquals("", UriEncoding.decode("", FORM_URL_ENCODED));
        assertEquals("foo", UriEncoding.decode("foo", FORM_URL_ENCODED));
        assertEquals("with space", UriEncoding.decode("with%20space", FORM_URL_ENCODED));
        assertEquals("with+PlusEncoded", UriEncoding.decode("with%2bPlusEncoded", FORM_URL_ENCODED));
        assertEquals("with PlusNotEncoded", UriEncoding.decode("with+PlusNotEncoded", FORM_URL_ENCODED));
        assertEquals(MANY_DIFFERENT_CHARS,
                UriEncoding.decode(MANY_DIFFERENT_CHARS_QUERY_PARAM_ENCODED_RFC3986, FORM_URL_ENCODED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeFormUrlEncodedInvalidSequence() {
        UriEncoding.decode("foo%2", RFC3986);
    }

}

