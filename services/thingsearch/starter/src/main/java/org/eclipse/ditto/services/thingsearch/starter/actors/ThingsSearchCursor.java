/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.starter.actors;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearchparser.RqlOptionParser;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.http.javadsl.coding.Coder;
import akka.stream.ActorMaterializer;
import akka.util.ByteString;
import scala.NotImplementedError;

/**
 * Package-private evaluator and generator of opaque cursors.
 */
final class ThingsSearchCursor {

    private static final Base64.Encoder BASE64_URL_ENCODER_WITHOUT_PADDING = Base64.getUrlEncoder().withoutPadding();

    // fields in string representation
    private static final JsonFieldDefinition<String> FILTER = JsonFactory.newStringFieldDefinition("F");
    private static final JsonFieldDefinition<String> JSON_FIELD_SELECTOR = JsonFactory.newStringFieldDefinition("J");
    private static final JsonFieldDefinition<String> CORRELATION_ID = JsonFactory.newStringFieldDefinition("C");
    private static final JsonFieldDefinition<String> OPTIONS = JsonFactory.newStringFieldDefinition("O");
    private static final JsonFieldDefinition<JsonArray> VALUES = JsonFactory.newJsonArrayFieldDefinition("V");

    // data encoded in a cursor
    private final String filter;
    private final String jsonFieldSelector;
    private final String correlationId;
    private final List<Option> options;
    private final JsonArray values;

    // convenient access to required option
    private final SortOption sortOption;

    private ThingsSearchCursor(final String filter, final String jsonFieldSelector, final String correlationId,
            final List<Option> options, final JsonArray values) {
        this.filter = filter;
        this.jsonFieldSelector = jsonFieldSelector;
        this.correlationId = correlationId;
        this.options = options;
        this.values = values;

        this.sortOption = findUniqueSortOption(options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, jsonFieldSelector, correlationId, options, values);
    }

    @Override
    public boolean equals(final Object that) {
        if (that instanceof ThingsSearchCursor) {
            final ThingsSearchCursor c = (ThingsSearchCursor) that;
            return Arrays.asList(filter, jsonFieldSelector, correlationId, options, values).equals(
                    Arrays.asList(c.filter, c.jsonFieldSelector, c.correlationId, c.options, c.values));
        } else {
            return false;
        }
    }

    String encode() {
        final ByteString byteString = ByteString.fromString(toJson().toString(), StandardCharsets.UTF_8);
        final ByteString compressed = Coder.Deflate.encode(byteString);
        return BASE64_URL_ENCODER_WITHOUT_PADDING.encodeToString(compressed.toByteBuffer().array());
    }

    // TODO: test decode/encode being inverse of each other
    static CompletionStage<ThingsSearchCursor> decode(final String cursorString,
            final ActorMaterializer materializer) {
        final ByteString compressed = ByteString.fromArray(Base64.getDecoder().decode(cursorString));
        return Coder.Deflate.decode(compressed, materializer)
                .thenApply(decompressed -> fromJson(JsonFactory.newObject(decompressed.utf8String())));
    }

    static CompletionStage<QueryThings> processQueryThings(final QueryThings queryThings) {
        throw new NotImplementedError(); // TODO
    }

    static SearchResult processSearchResult(final QueryThings queryThings, final SearchResult searchResult) {
        throw new NotImplementedError(); // TODO
    }

    private JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(FILTER, filter)
                .set(JSON_FIELD_SELECTOR, jsonFieldSelector)
                .set(CORRELATION_ID, correlationId)
                .set(OPTIONS, RqlOptionParser.unparse(options))
                .set(VALUES, values)
                .build();
    }

    private static SortOption findUniqueSortOption(final List<Option> options) {
        final Class<SortOption> clazz = SortOption.class;
        final List<SortOption> sortOptions = options.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.toList());
        if (sortOptions.size() != 1) {
            throw invalidCursor();
        } else {
            return sortOptions.get(0);
        }
    }

    private static InvalidOptionException invalidCursor() {
        return InvalidOptionException.newBuilder()
                .message("The option 'cursor' is invalid.")
                .build();
    }

    private static ThingsSearchCursor fromJson(final JsonObject json) {
        return new ThingsSearchCursor(
                json.getValueOrThrow(FILTER),
                json.getValueOrThrow(JSON_FIELD_SELECTOR),
                json.getValueOrThrow(CORRELATION_ID),
                RqlOptionParser.parseOptions(json.getValueOrThrow(OPTIONS)),
                json.getValueOrThrow(VALUES));
    }
}
