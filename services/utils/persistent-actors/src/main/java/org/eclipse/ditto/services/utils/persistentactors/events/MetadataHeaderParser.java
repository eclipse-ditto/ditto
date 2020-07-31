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
package org.eclipse.ditto.services.utils.persistentactors.events;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Parses DittoHeaders to obtain the included MetadataHeaders.
 *
 * @since 1.2.0
 */
@Immutable
final class MetadataHeaderParser {

    private MetadataHeaderParser() {
        super();
    }

    /**
     * Returns an instance of this class.
     *
     * @return the instance.
     */
    static MetadataHeaderParser getInstance() {
        return new MetadataHeaderParser();
    }

    /**
     * Parses MetadataHeaders from the given DittoHeaders.
     *
     * @param dittoHeaders the DittoHeaders to be parsed.
     * @return a stream of the parsed metadata headers.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     * @throws IllegalArgumentException if the key of a metadata header
     * <ul>
     *     <li>is empty,</li>
     *     <li>starts with an asterisk ({@code *}) and has not exactly two levels,</li>
     *     <li>contains an asterisk at any level but the first.</li>
     * </ul>
     * @throws org.eclipse.ditto.json.JsonParseException if the value of a metadata header cannot be parsed.
     */
    Stream<MetadataHeader> parse(final DittoHeaders dittoHeaders) {
        checkNotNull(dittoHeaders, "dittoHeaders");
        final Stream.Builder<MetadataHeader> streamBuilder = Stream.builder();
        dittoHeaders.forEach((key, value) -> {
            if (key.startsWith(MetadataHeaderKey.PREFIX)) {
                streamBuilder.accept(MetadataHeader.of(parseKey(key), parseValue(value)));
            }
        });
        return streamBuilder.build();
    }

    private static MetadataHeaderKey parseKey(final CharSequence key) {
        return MetadataHeaderKey.parse(key);
    }

    private static JsonValue parseValue(final String value) {
        return JsonFactory.readFrom(value);
    }

}
