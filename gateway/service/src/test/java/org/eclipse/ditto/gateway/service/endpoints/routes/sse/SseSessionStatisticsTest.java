/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.MdcEntry;
import org.junit.Test;

/**
 * Unit tests for {@link SseSessionStatistics}.
 */
public final class SseSessionStatisticsTest {

    private static final String PATH = "/api/2/things";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("sse-session-statistics-test")
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.JWT,
                    AuthorizationSubject.newInstance("integration:installer"),
                    AuthorizationSubject.newInstance("jwt-sub:user-1")))
            .build();

    @Test
    public void mdcEntriesContainClientDetailsOfRequest() {
        final HttpRequest request = HttpRequest.GET(PATH)
                .addHeader(RawHeader.create("Authorization", "Bearer " + jwtWithBody("{\"sub\":\"user-1\"," +
                        "\"azp\":\"installer-app\"}")))
                .addHeader(RawHeader.create("Origin", "https://installer.example.com"))
                .addHeader(RawHeader.create("User-Agent", "Mozilla/5.0 (Ünïcödé)"));

        final Map<String, String> mdc = mdcOf(SseSessionStatistics.start(request, DITTO_HEADERS, true, true));

        assertThat(mdc)
                .containsEntry(SseSessionStatistics.MDC_PATH, PATH)
                .containsEntry(SseSessionStatistics.MDC_SUBJECTS, "integration:installer,jwt-sub:user-1")
                .containsEntry(SseSessionStatistics.MDC_AUTHORIZED_PARTY, "installer-app")
                .containsEntry(SseSessionStatistics.MDC_ORIGIN, "https://installer.example.com")
                .containsEntry(SseSessionStatistics.MDC_USER_AGENT, "Mozilla/5.0 (Ünïcödé)")
                .containsEntry(SseSessionStatistics.MDC_EXTRA_FIELDS, "true")
                .containsEntry(SseSessionStatistics.MDC_FILTER, "true");
    }

    @Test
    public void mdcEntriesOmitClientDetailsWhichAreNotPresent() {
        final HttpRequest request = HttpRequest.GET(PATH);

        final Map<String, String> mdc =
                mdcOf(SseSessionStatistics.start(request, DittoHeaders.empty(), false, false));

        assertThat(mdc)
                .containsEntry(SseSessionStatistics.MDC_SUBJECTS, "")
                .containsEntry(SseSessionStatistics.MDC_EXTRA_FIELDS, "false")
                .containsEntry(SseSessionStatistics.MDC_FILTER, "false")
                .doesNotContainKeys(SseSessionStatistics.MDC_AUTHORIZED_PARTY, SseSessionStatistics.MDC_ORIGIN,
                        SseSessionStatistics.MDC_USER_AGENT);
    }

    @Test
    public void authorizedPartyIsOmittedForJwtWithoutAzpClaim() {
        final HttpRequest request = HttpRequest.GET(PATH)
                .addHeader(RawHeader.create("Authorization", "Bearer " + jwtWithBody("{\"sub\":\"user-1\"}")));

        assertThat(mdcOf(SseSessionStatistics.start(request, DITTO_HEADERS, false, false)))
                .doesNotContainKey(SseSessionStatistics.MDC_AUTHORIZED_PARTY);
    }

    @Test
    public void authorizedPartyIsOmittedForNonBearerAuthorization() {
        final HttpRequest request = HttpRequest.GET(PATH)
                .addHeader(RawHeader.create("Authorization", "Basic ZGl0dG86ZGl0dG8="));

        assertThat(mdcOf(SseSessionStatistics.start(request, DITTO_HEADERS, false, false)))
                .doesNotContainKey(SseSessionStatistics.MDC_AUTHORIZED_PARTY);
    }

    @Test
    public void authorizedPartyIsOmittedForMalformedBearerToken() {
        final HttpRequest malformedToken = HttpRequest.GET(PATH)
                .addHeader(RawHeader.create("Authorization", "Bearer not-a-jwt"));
        final HttpRequest bodyNoJsonObject = HttpRequest.GET(PATH)
                .addHeader(RawHeader.create("Authorization", "Bearer " + jwtWithBody("[1,2,3]")));

        assertThat(mdcOf(SseSessionStatistics.start(malformedToken, DITTO_HEADERS, false, false)))
                .doesNotContainKey(SseSessionStatistics.MDC_AUTHORIZED_PARTY);
        assertThat(mdcOf(SseSessionStatistics.start(bodyNoJsonObject, DITTO_HEADERS, false, false)))
                .doesNotContainKey(SseSessionStatistics.MDC_AUTHORIZED_PARTY);
    }

    @Test
    public void countsAreInitiallyZero() {
        final SseSessionStatistics underTest = newStatistics();

        assertThat(mdcOf(underTest))
                .containsEntry(SseSessionStatistics.MDC_RECEIVED, "0")
                .containsEntry(SseSessionStatistics.MDC_TARGETED, "0")
                .containsEntry(SseSessionStatistics.MDC_FILTERED_OUT, "0")
                .containsEntry(SseSessionStatistics.MDC_ENRICHMENT_FAILED, "0")
                .containsEntry(SseSessionStatistics.MDC_DELIVERED, "0");
    }

    @Test
    public void countsSignalsAndEvents() {
        final SseSessionStatistics underTest = newStatistics();

        IntStream.range(0, 5).forEach(i -> underTest.signalReceived());
        IntStream.range(0, 3).forEach(i -> underTest.signalTargeted());
        IntStream.range(0, 2).forEach(i -> underTest.signalFilteredOut());
        underTest.enrichmentFailed();
        underTest.eventDelivered();

        assertThat(mdcOf(underTest))
                .containsEntry(SseSessionStatistics.MDC_RECEIVED, "5")
                .containsEntry(SseSessionStatistics.MDC_TARGETED, "3")
                .containsEntry(SseSessionStatistics.MDC_FILTERED_OUT, "2")
                .containsEntry(SseSessionStatistics.MDC_ENRICHMENT_FAILED, "1")
                .containsEntry(SseSessionStatistics.MDC_DELIVERED, "1");
    }

    @Test
    public void countsSignalsReceivedConcurrently() {
        final SseSessionStatistics underTest = newStatistics();

        CompletableFuture.allOf(IntStream.range(0, 8)
                        .mapToObj(i -> CompletableFuture.runAsync(() ->
                                IntStream.range(0, 1000).forEach(j -> underTest.signalReceived())))
                        .toArray(CompletableFuture[]::new))
                .join();

        assertThat(underTest.getReceived()).isEqualTo(8000L);
    }

    @Test
    public void durationIsAddedToMdcEntries() {
        final SseSessionStatistics underTest = newStatistics();

        assertThat(toMap(underTest, 42L)).containsEntry(SseSessionStatistics.MDC_DURATION_SECONDS, "42");
    }

    @Test
    public void logSummaryDoesNotThrow() {
        final SseSessionStatistics underTest = newStatistics();

        assertThatCode(() -> underTest.logSummary(null)).doesNotThrowAnyException();
        assertThatCode(() -> underTest.logSummary(new IllegalStateException("aborted")))
                .doesNotThrowAnyException();
        assertThatCode(() -> underTest.logSummary(new IllegalStateException())).doesNotThrowAnyException();
    }

    private static SseSessionStatistics newStatistics() {
        return SseSessionStatistics.start(HttpRequest.GET(PATH), DITTO_HEADERS, false, false);
    }

    private static Map<String, String> mdcOf(final SseSessionStatistics statistics) {
        return toMap(statistics, 0L);
    }

    private static Map<String, String> toMap(final SseSessionStatistics statistics, final long durationSeconds) {
        return statistics.getMdcEntries(durationSeconds)
                .stream()
                .collect(Collectors.toMap(MdcEntry::getKey, MdcEntry::getValueOrNull));
    }

    private static String jwtWithBody(final String body) {
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) +
                "." + encoder.encodeToString(body.getBytes(StandardCharsets.UTF_8)) +
                ".c2lnbmF0dXJl";
    }

}
