/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pekko.logging;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;

/**
 * An enumeration of commonly known MDC entry keys.
 *
 * @since 1.4.0
 */
public enum CommonMdcEntryKey implements CharSequence {

    CORRELATION_ID(DittoHeaderDefinition.CORRELATION_ID.getKey()),
    DITTO_LOG_TAG("ditto-log-tag"),
    TRACE_PARENT(DittoHeaderDefinition.W3C_TRACEPARENT.getKey());

    private static final String TRACE_ID = TRACE_PARENT.key + "-trace-id";
    private static final String SPAN_ID = TRACE_PARENT.key + "-span-id";

    private final String key;

    CommonMdcEntryKey(final String key) {
        this.key = key;
    }

    @Override
    public int length() {
        return key.length();
    }

    @Override
    public char charAt(final int index) {
        return key.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return key.subSequence(start, end);
    }

    @Override
    public String toString() {
        return key;
    }

    /**
     * Returns the {@code CommonMdcEntryKey} with the given name.
     *
     * @param name the name of the common MDC entry to get.
     * @return the common MDC entry with the given name or an empty optional.
     */
    public static Optional<CommonMdcEntryKey> forName(@Nullable final CharSequence name) {
        return Stream.of(values())
                .filter(l -> Objects.equals(l.key, String.valueOf(name)))
                .findAny();
    }

    /**
     * Extracts a list of MdcEntries based on the given {@code headers} map, picking out explicitly headers which should
     * be added to the MDC.
     * <p>
     * This method is called a lot of times in Ditto's codebase and therefore better be fast. This was achieved by not
     * iterating over all headers, but to accessing the passed {@code HashMap} for picking out only the supported
     * headers.
     * </p>
     *
     * @param headers the headers to look into for extracting the MDC worthy headers to log.
     * @return a list of MDC entries to add to the MDC.
     */
    public static List<MdcEntry> extractMdcEntriesFromHeaders(@Nullable final Map<String, String> headers) {

        if (null == headers || headers.isEmpty()) {
            return Collections.emptyList();
        }
        return Stream.of(CORRELATION_ID, TRACE_PARENT)
                .flatMap(mdcEntryKey -> extractValue(headers, mdcEntryKey))
                .toList();
    }

    private static Stream<MdcEntry> extractValue(final Map<String, String> headers,
            final CommonMdcEntryKey mdcEntryKey) {

        if (mdcEntryKey == TRACE_PARENT) {
            return Optional.ofNullable(headers.get(mdcEntryKey.key))
                    .filter(traceParent -> traceParent.charAt(2) == '-' && traceParent.length() == 55)
                    .map(traceParent -> Stream.of(
                            // positions defined by https://www.w3.org/TR/trace-context/#traceparent-header-field-values to contain the "trace-id"
                            MdcEntry.of(TRACE_ID, traceParent.substring(3, 35)),
                            MdcEntry.of(SPAN_ID, traceParent.substring(36, 52))
                    )).stream()
                    .flatMap(Function.identity());
        } else {
            return Optional.ofNullable(headers.get(mdcEntryKey.key))
                    .map(value -> MdcEntry.of(mdcEntryKey.key, value))
                    .stream();
        }
    }

}
