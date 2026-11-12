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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authorization.AuthorizationHeaderJwtExtractor;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.MdcEntry;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Collects statistics of a single SSE session and logs them as one summary line once the session ended.
 * The details are added as MDC entries, so that structured log tooling can aggregate the consumed events e.g. per
 * subject, client or origin of the session.
 */
@ThreadSafe
final class SseSessionStatistics {

    static final String MDC_PATH = "sse-path";
    static final String MDC_SUBJECTS = "sse-subjects";
    static final String MDC_AUTHORIZED_PARTY = "sse-azp";
    static final String MDC_ORIGIN = "sse-origin";
    static final String MDC_USER_AGENT = "sse-user-agent";
    static final String MDC_EXTRA_FIELDS = "sse-extra-fields";
    static final String MDC_FILTER = "sse-filter";
    static final String MDC_DURATION_SECONDS = "sse-duration-seconds";
    static final String MDC_RECEIVED = "sse-received";
    static final String MDC_TARGETED = "sse-targeted";
    static final String MDC_FILTERED_OUT = "sse-filtered-out";
    static final String MDC_ENRICHMENT_FAILED = "sse-enrichment-failed";
    static final String MDC_DELIVERED = "sse-delivered";

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(SseSessionStatistics.class);

    private static final String ORIGIN_HEADER = "Origin";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Nullable private final String correlationId;
    private final String path;
    private final String subjects;
    @Nullable private final String authorizedParty;
    @Nullable private final String origin;
    @Nullable private final String userAgent;
    private final boolean extraFieldsRequested;
    private final boolean filterRequested;
    private final long startNanos;
    private final LongAdder received;
    private final LongAdder targeted;
    private final LongAdder filteredOut;
    private final LongAdder enrichmentFailed;
    private final LongAdder delivered;

    private SseSessionStatistics(final HttpRequest request, final DittoHeaders dittoHeaders,
            final boolean extraFieldsRequested, final boolean filterRequested) {

        correlationId = dittoHeaders.getCorrelationId().orElse(null);
        path = request.getUri().path();
        subjects = String.join(",", dittoHeaders.getAuthorizationContext().getAuthorizationSubjectIds());
        authorizedParty = getAuthorizedParty(request);
        origin = getHeaderValue(request, ORIGIN_HEADER).orElse(null);
        userAgent = getHeaderValue(request, USER_AGENT_HEADER).orElse(null);
        this.extraFieldsRequested = extraFieldsRequested;
        this.filterRequested = filterRequested;
        startNanos = System.nanoTime();
        received = new LongAdder();
        targeted = new LongAdder();
        filteredOut = new LongAdder();
        enrichmentFailed = new LongAdder();
        delivered = new LongAdder();
    }

    /**
     * Starts collecting statistics for a new SSE session.
     *
     * @param request the HTTP request which opened the SSE session.
     * @param dittoHeaders the headers of the session containing its correlation ID and authorization context.
     * @param extraFieldsRequested whether the session requested enrichment of its events with extra fields.
     * @param filterRequested whether the session requested filtering of its events by an RQL filter.
     * @return the new statistics.
     */
    static SseSessionStatistics start(final HttpRequest request, final DittoHeaders dittoHeaders,
            final boolean extraFieldsRequested, final boolean filterRequested) {

        return new SseSessionStatistics(request, dittoHeaders, extraFieldsRequested, filterRequested);
    }

    /**
     * Counts a signal which reached the SSE stream of the session.
     * The streaming session actor already dropped signals the session is not allowed to read or which do not match
     * its namespaces before, those are not counted.
     */
    void signalReceived() {
        received.increment();
    }

    /**
     * Counts a signal which targets the thing IDs and namespaces of the session and is therefore post-processed
     * (including the retrieval of extra fields, if requested).
     */
    void signalTargeted() {
        targeted.increment();
    }

    /**
     * Counts a targeted signal which was not delivered because it did not match the RQL filter of the session.
     */
    void signalFilteredOut() {
        filteredOut.increment();
    }

    /**
     * Counts a targeted signal for which the retrieval of the requested extra fields failed.
     */
    void enrichmentFailed() {
        enrichmentFailed.increment();
    }

    /**
     * Counts an event which was delivered to the SSE client, including errors emitted to the client instead of an
     * event, e.g. because the retrieval of extra fields failed.
     */
    void eventDelivered() {
        delivered.increment();
    }

    long getReceived() {
        return received.sum();
    }

    long getTargeted() {
        return targeted.sum();
    }

    long getFilteredOut() {
        return filteredOut.sum();
    }

    long getEnrichmentFailed() {
        return enrichmentFailed.sum();
    }

    long getDelivered() {
        return delivered.sum();
    }

    /**
     * Logs the summary of the session.
     *
     * @param error the error the session terminated with or {@code null} if it terminated regularly.
     */
    void logSummary(@Nullable final Throwable error) {
        final long durationSeconds = Duration.ofNanos(System.nanoTime() - startNanos).toSeconds();
        final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(correlationId)
                .withMdcEntries(getMdcEntries(durationSeconds));
        if (null == error) {
            logger.info("SSE session on <{}> ended after <{}s>: received <{}>, targeted <{}>, filtered out <{}>, " +
                            "enrichment failed <{}>, delivered <{}> events.", path, durationSeconds, getReceived(),
                    getTargeted(), getFilteredOut(), getEnrichmentFailed(), getDelivered());
        } else {
            logger.info("SSE session on <{}> ended after <{}s> with <{}: {}>: received <{}>, targeted <{}>, " +
                            "filtered out <{}>, enrichment failed <{}>, delivered <{}> events.", path,
                    durationSeconds, error.getClass().getSimpleName(), error.getMessage(), getReceived(),
                    getTargeted(), getFilteredOut(), getEnrichmentFailed(), getDelivered());
        }
    }

    List<MdcEntry> getMdcEntries(final long durationSeconds) {
        final List<MdcEntry> mdcEntries = new ArrayList<>();
        mdcEntries.add(MdcEntry.of(MDC_PATH, path));
        mdcEntries.add(MdcEntry.of(MDC_SUBJECTS, subjects));
        if (null != authorizedParty) {
            mdcEntries.add(MdcEntry.of(MDC_AUTHORIZED_PARTY, authorizedParty));
        }
        if (null != origin) {
            mdcEntries.add(MdcEntry.of(MDC_ORIGIN, origin));
        }
        if (null != userAgent) {
            mdcEntries.add(MdcEntry.of(MDC_USER_AGENT, userAgent));
        }
        mdcEntries.add(MdcEntry.of(MDC_EXTRA_FIELDS, String.valueOf(extraFieldsRequested)));
        mdcEntries.add(MdcEntry.of(MDC_FILTER, String.valueOf(filterRequested)));
        mdcEntries.add(MdcEntry.of(MDC_DURATION_SECONDS, String.valueOf(durationSeconds)));
        mdcEntries.add(MdcEntry.of(MDC_RECEIVED, String.valueOf(getReceived())));
        mdcEntries.add(MdcEntry.of(MDC_TARGETED, String.valueOf(getTargeted())));
        mdcEntries.add(MdcEntry.of(MDC_FILTERED_OUT, String.valueOf(getFilteredOut())));
        mdcEntries.add(MdcEntry.of(MDC_ENRICHMENT_FAILED, String.valueOf(getEnrichmentFailed())));
        mdcEntries.add(MdcEntry.of(MDC_DELIVERED, String.valueOf(getDelivered())));
        return mdcEntries;
    }

    @Nullable
    private static String getAuthorizedParty(final HttpRequest request) {
        try {
            return getHeaderValue(request, AUTHORIZATION_HEADER)
                    .flatMap(AuthorizationHeaderJwtExtractor::extractJwt)
                    .map(JsonWebToken::getAuthorizedParty)
                    .filter(azp -> !azp.isEmpty())
                    .orElse(null);
        } catch (final RuntimeException e) {
            // the statistics are only informational and must never fail the SSE session
            return null;
        }
    }

    private static Optional<String> getHeaderValue(final HttpRequest request, final String headerName) {
        return request.getHeader(headerName).map(HttpHeader::value);
    }

}
