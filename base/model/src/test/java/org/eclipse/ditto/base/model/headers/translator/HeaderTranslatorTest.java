/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.headers.translator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.data.MapEntry;
import org.assertj.core.util.Lists;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests {@link HeaderTranslator}
 */
public final class HeaderTranslatorTest {

    @Test
    public void testCaseInsensitivity() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put("lower-case%header", "hello%world");
        externalHeaders.put("mIxEd-Case!HEadER", "heLLO!WORld");
        externalHeaders.put("UPPER-CASE@HEADER", "HELLO@WORLD");

        final Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("lower-case%header", "hello%world");
        expectedHeaders.put("mixed-case!header", "heLLO!WORld");
        expectedHeaders.put("upper-case@header", "HELLO@WORLD");

        assertThat(DittoHeaders.of(underTest.fromExternalHeaders(externalHeaders))).isEqualTo(expectedHeaders);
    }

    @Test
    public void testNullValues() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), "true");
        externalHeaders.put("nullValueHeader", null);

        assertThat(underTest.fromExternalHeaders(externalHeaders))
                .containsOnlyKeys(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey());
    }

    @Test
    public void tryToTranslateNullExternalHeadersToDittoHeaders() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.fromExternalHeaders(null))
                .withMessage("The externalHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void translateEmptyExternalHeadersToDittoHeaders() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.fromExternalHeaders(new HashMap<>())).isEmpty();
    }

    @Test
    public void translateExternalOnlyHeadersToDittoHeaders() {
        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put(DittoHeaderDefinition.ETAG.getKey(), "\"-12124212\"");
        externalHeaders.put(DittoHeaderDefinition.ORIGINATOR.getKey(), "Nyarlathotep");

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.fromExternalHeaders(externalHeaders)).isEmpty();
    }

    @Test
    public void testReadExternalAllowedHeader() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put("Authorization", "Basic afhdfiusfaifsafwaihfidsahfiudsafidsahfoidsaf");

        assertThat(DittoHeaders.of(underTest.fromExternalHeaders(externalHeaders))).containsKey("authorization");
    }

    @Test
    public void testHeaderFiltering() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().putHeader("bumlux", "foobar").build();

        assertThat(underTest.toExternalAndRetainKnownHeaders(dittoHeaders)).isEmpty();
    }

    @Test
    public void translateMixedExternalHeadersToDittoHeaders() {
        final String correlationId = "correlation-id";
        final Map<String, String> externalHeaders = new HashMap<>();
        externalHeaders.put(DittoHeaderDefinition.ETAG.getKey(), "\"-12124212\"");
        externalHeaders.put(DittoHeaderDefinition.ORIGINATOR.getKey(), "Nyarlathotep");
        externalHeaders.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        externalHeaders.put("foo", "bar");
        final DittoHeaders expected = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .putHeader("foo", "bar")
                .build();

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.fromExternalHeaders(externalHeaders)).isEqualTo(expected);
    }

    @Test
    public void tryToTranslateNullToExternalHeaders() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.toExternalHeaders(null))
                .withMessage("The dittoHeaders must not be null!")
                .withNoCause();
    }

    @Test
    public void translateEmptyDittoHeadersToExternal() {
        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.toExternalHeaders(DittoHeaders.empty())).isEmpty();
    }

    @Test
    public void translateInternalOnlyDittoHeadersToExternal() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .dryRun(true)
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("foo"), AuthorizationSubject.newInstance("bar")))
                .origin("Cthulhu")
                .build();

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.toExternalHeaders(dittoHeaders)).isEmpty();
    }

    @Test
    public void translateMixedDittoHeadersToExternal() {
        final String correlationId = "correlation-id";
        final MapEntry<String, String> customHeader = entry("foo", "bar");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .dryRun(true)
                .correlationId(correlationId)
                .putHeader(customHeader.getKey(), customHeader.getValue())
                .build();
        final Map<String, String> expected = new HashMap<>();
        expected.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        expected.put(customHeader.getKey(), customHeader.getValue());

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.toExternalHeaders(dittoHeaders)).isEqualTo(expected);
    }

    @Test
    public void translateDittoHeadersWithAckRequestToExternal() {
        final String correlationId = "correlation-id";
        final List<AcknowledgementRequest> allAcknowledgementRequests = Lists.list(
                AcknowledgementRequest.of(AcknowledgementLabel.of("foo")),
                AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                AcknowledgementRequest.of(AcknowledgementLabel.of("bar")));
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequests(allAcknowledgementRequests)
                .responseRequired(true)
                .build();
        final JsonArray allAcknowledgementRequestsJsonArray = allAcknowledgementRequests.stream()
                .map(AcknowledgementRequest::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
        final JsonArray externalAcknowledgementRequests = allAcknowledgementRequestsJsonArray.toBuilder()
                .remove(1)
                .build();
        final Map<String, String> expected = new HashMap<>();
        expected.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        expected.put(DittoHeaderDefinition.REQUESTED_ACKS.getKey(), externalAcknowledgementRequests.toString());
        expected.put(DittoHeaderDefinition.RESPONSE_REQUIRED.getKey(), Boolean.TRUE.toString());

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.toExternalHeaders(dittoHeaders)).isEqualTo(expected);
    }

    @Test
    public void translateMixedDittoHeadersRetainKnownHeaders() {
        final String correlationId = "correlation-id";
        final MapEntry<String, String> customHeader = entry("foo", "bar");
        final MapEntry<String, String> customHeader2 = entry("websocket", "example-ws-header-value");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .dryRun(true)
                .correlationId(correlationId)
                .putHeader(customHeader.getKey(), customHeader.getValue())
                .putHeader(customHeader2.getKey(), customHeader2.getValue())
                .build();
        final Map<String, String> expected = new HashMap<>();
        expected.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId);
        expected.put(DittoHeaderDefinition.DRY_RUN.getKey(), Boolean.TRUE.toString());

        final HeaderTranslator underTest = HeaderTranslator.of(DittoHeaderDefinition.values());

        assertThat(underTest.retainKnownHeaders(dittoHeaders)).isEqualTo(expected);
    }

}
