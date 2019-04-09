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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.model.thingsearch.CursorOption;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.OptionVisitor;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.model.thingsearch.SizeOption;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
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

    private static final List<Class<?>> PERMITTED_OPTIONS = Arrays.asList(CursorOption.class, SizeOption.class);

    private static final Base64.Encoder BASE64_URL_ENCODER_WITHOUT_PADDING = Base64.getUrlEncoder().withoutPadding();

    // fields in string representation
    private static final JsonFieldDefinition<String> FILTER = JsonFactory.newStringFieldDefinition("F");
    private static final JsonFieldDefinition<String> JSON_FIELD_SELECTOR = JsonFactory.newStringFieldDefinition("J");
    private static final JsonFieldDefinition<String> CORRELATION_ID = JsonFactory.newStringFieldDefinition("C");
    private static final JsonFieldDefinition<String> OPTIONS = JsonFactory.newStringFieldDefinition("O");
    private static final JsonFieldDefinition<JsonArray> VALUES = JsonFactory.newJsonArrayFieldDefinition("V");

    // data encoded in a cursor
    @Nullable private final String filter;
    @Nullable private final String jsonFieldSelector;
    @Nullable private final Set<String> namespaces;
    private final DittoHeaders dittoHeaders;
    private final List<Option> options;
    private final JsonArray values;

    // convenient access to required option
    private final SortOption sortOption;

    private ThingsSearchCursor(@Nullable final String jsonFieldSelector,
            @Nullable final Set<String> namespaces, final DittoHeaders dittoHeaders,
            final List<Option> options, @Nullable final String filter, final JsonArray values) {
        this.namespaces = namespaces;
        this.filter = filter;
        this.jsonFieldSelector = jsonFieldSelector;
        this.dittoHeaders = dittoHeaders;
        this.options = options;
        this.values = values;

        this.sortOption = findUniqueSortOption(options);

        if (sortOption.getSize() != values.getSize()) {
            throw invalidCursor();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, jsonFieldSelector, dittoHeaders, options, values);
    }

    @Override
    public boolean equals(final Object that) {
        if (that instanceof ThingsSearchCursor) {
            final ThingsSearchCursor c = (ThingsSearchCursor) that;
            return Arrays.asList(filter, jsonFieldSelector, dittoHeaders, options, values).equals(
                    Arrays.asList(c.filter, c.jsonFieldSelector, c.dittoHeaders, c.options, c.values));
        } else {
            return false;
        }
    }

    private String computeFilterString(final CriteriaFactory cf) {
        return ToStringCriteriaVisitor.concatCriteria(
                getNextPageFilter(sortOption, values, cf).accept(new ToStringCriteriaVisitor()),
                filter);
    }

    private ThingsSearchCursor override(final QueryThings queryThings, final List<Option> inputOptions) {
        if (hasForbiddenFields(queryThings, inputOptions)) {
            // TODO: specialize "has forbidden fields" error
            throw invalidCursor();
        }
        final String newSelector =
                queryThings.getFields().map(JsonFieldSelector::toString).orElse(this.jsonFieldSelector);
        final List<Option> newOptions = new OptionsBuilder().visitAll(options).visitAll(inputOptions).build();
        final DittoHeaders newHeaders = dittoHeaders.toBuilder().putHeaders(queryThings.getDittoHeaders()).build();
        return new ThingsSearchCursor(newSelector, namespaces, newHeaders, newOptions, filter, values);
    }

    private SearchResult searchResultWithExistingCursor(final SearchResult searchResult) {
        final JsonArray newValues = getNewValues(searchResult);
        final ThingsSearchCursor newCursor =
                new ThingsSearchCursor(jsonFieldSelector, namespaces, dittoHeaders, options, filter, newValues);
        return searchResult.toBuilder()
                .cursor(newCursor.encode())
                .nextPageOffset(null)
                .build();
    }

    QueryThings toQueryThings(final CriteriaFactory cf) {
        final List<String> optionStrings = options.stream().map(Option::toString).collect(Collectors.toList());
        final JsonFieldSelector selector = Optional.ofNullable(jsonFieldSelector)
                .map(JsonFieldSelector::newInstance)
                .orElse(null);
        return QueryThings.of(computeFilterString(cf), optionStrings, selector, namespaces, dittoHeaders);
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

    static CompletionStage<Optional<ThingsSearchCursor>> processQueryThings(final QueryThings queryThings,
            final ActorMaterializer materializer) {
        final List<Option> options = getOptions(queryThings);
        final List<CursorOption> cursorOptions = findAll(CursorOption.class, options);
        if (cursorOptions.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else if (cursorOptions.size() == 1) {
            return decode(cursorOptions.get(0).getCursor(), materializer)
                    .thenApply(cursor -> Optional.of(cursor.override(queryThings, options)));
        } else {
            // there may not be 2 or more cursor options in 1 command.
            throw invalidCursor();
        }
    }

    static SearchResult processSearchResult(final QueryThings queryThings,
            final Query query,
            @Nullable final ThingsSearchCursor cursor,
            final SearchResult searchResult,
            final CriteriaFactory cf) {

        if (!findAll(LimitOption.class, getOptions(queryThings)).isEmpty()) {
            // do not deliver cursor if skip is nonzero
            return searchResult;
        } else if (cursor != null) {
            // adjust next cursor by search result, do not deliver nextPageOffset
            return cursor.searchResultWithExistingCursor(searchResult);
        } else {
            // compute new cursor, deliver both
            return searchResultWithNewCursor(queryThings, query, searchResult, cf);
        }
    }

    private JsonObject toJson() {
        return JsonFactory.newObjectBuilder()
                .set(FILTER, filter)
                .set(JSON_FIELD_SELECTOR, jsonFieldSelector)
                .set(CORRELATION_ID, dittoHeaders.getCorrelationId().orElse(null))
                .set(OPTIONS, RqlOptionParser.unparse(options))
                .set(VALUES, values)
                .build();
    }

    private static <T> List<T> findAll(final Class<T> clazz, final Collection<?> collection) {
        return collection.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

    private static SortOption findUniqueSortOption(final List<Option> options) {
        final List<SortOption> sortOptions = findAll(SortOption.class, options);
        if (sortOptions.size() == 1) {
            return sortOptions.get(0);
        } else {
            throw invalidCursor();
        }
    }

    private static InvalidOptionException invalidCursor() {
        return InvalidOptionException.newBuilder()
                .message("The option 'cursor' is invalid.")
                .build();
    }

    private static ThingsSearchCursor fromJson(final JsonObject json) {
        return new ThingsSearchCursor(
                json.getValueOrThrow(JSON_FIELD_SELECTOR),
                null,
                json.getValue(CORRELATION_ID).map(ThingsSearchCursor::correlationIdHeader).orElse(DittoHeaders.empty()),
                RqlOptionParser.parseOptions(json.getValueOrThrow(OPTIONS)),
                json.getValueOrThrow(FILTER),
                json.getValueOrThrow(VALUES));
    }

    private static List<Option> getOptions(final QueryThings queryThings) {
        return queryThings.getOptions().orElse(Collections.emptyList()).stream()
                .map(RqlOptionParser::parseOptions)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private static boolean hasForbiddenFields(final QueryThings queryThings, final List<Option> otherOptions) {
        // when a cursor is present, the command may only have an additional size option and a field selector.
        return queryThings.getFilter().isPresent() ||
                otherOptions.size() > 2 ||
                !otherOptions.stream().map(Option::getClass).allMatch(PERMITTED_OPTIONS::contains);
    }

    // TODO: remove if it does not work
    private static DittoHeaders correlationIdHeader(final String correlationId) {
        return DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .build();
    }

    private static SearchResult searchResultWithNewCursor(final QueryThings queryThings, final Query query,
            final SearchResult searchResult, final CriteriaFactory cf) {

        final ThingsSearchCursor newCursor = computeNewCursor(queryThings, query, searchResult, cf);
        return searchResult.toBuilder()
                .cursor(newCursor.encode())
                .build();
    }

    private static ThingsSearchCursor computeNewCursor(final QueryThings queryThings, final Query query,
            final SearchResult searchResult, final CriteriaFactory cf) {

        // TODO: implement
        throw new NotImplementedError();
    }

    private static Criteria getNextPageFilter(final SortOption sortOption,
            final JsonArray previousValues,
            final CriteriaFactory cf) {

        if (sortOption.getSize() != previousValues.getSize()) {
            throw invalidCursor();
        }
        return getNextPageFilterImpl(sortOption.getEntries(), previousValues, cf, 0);
    }

    private static Criteria getNextPageFilterImpl(final List<SortOptionEntry> sortOptionEntries,
            final JsonArray previousValues, final CriteriaFactory cf, final int i) {

        final SortOptionEntry sortOptionEntry = sortOptionEntries.get(i);
        final JsonValue previousValue = previousValues.get(i).orElse(JsonFactory.nullLiteral());
        final Criteria ithDimensionCriteria = cf.fieldCriteria(
                toFieldExpression(sortOptionEntry.getPropertyPath()),
                getPredicate(sortOptionEntry, previousValue, cf));
        if (i + 1 >= sortOptionEntries.size()) {
            return ithDimensionCriteria;
        } else {
            final Criteria ithDimensionEq =
                    cf.fieldCriteria(toFieldExpression(sortOptionEntry.getPropertyPath()), cf.eq(previousValue));
            final Criteria nextDimension = getNextPageFilterImpl(sortOptionEntries, previousValues, cf, i + 1);
            return cf.or(Arrays.asList(ithDimensionCriteria, cf.and(Arrays.asList(ithDimensionEq, nextDimension))));
        }
    }

    private static Predicate getPredicate(final SortOptionEntry sortOptionEntry,
            final JsonValue previousValue, final CriteriaFactory cf) {

        switch (sortOptionEntry.getOrder()) {
            case DESC:
                return cf.lt(previousValue);
            case ASC:
            default:
                return cf.gt(previousValue);
        }
    }

    private static FilterFieldExpression toFieldExpression(final CharSequence pointer) {
        return new SimpleFieldExpressionImpl(pointer.toString());
    }

    private static JsonArray getNewValues(final SearchResult searchResult) {
        // TODO: implement
        throw new NotImplementedError();
    }

    private static final class OptionsBuilder implements OptionVisitor {

        @Nullable private SortOption sortOption;
        @Nullable private SizeOption sizeOption;
        @Nullable private CursorOption cursorOption;

        private OptionsBuilder visitAll(final List<Option> startingOptions) {
            startingOptions.forEach(option -> option.accept(this));
            return this;
        }

        @Override
        public void visit(final LimitOption limitOption) {
            throw invalidCursor();
        }

        @Override
        public void visit(final SortOption sortOption) {
            this.sortOption = sortOption;
        }

        @Override
        public void visit(final CursorOption cursorOption) {
            this.cursorOption = cursorOption;
        }

        @Override
        public void visit(final SizeOption sizeOption) {
            this.sizeOption = sizeOption;
        }

        private List<Option> build() {
            return Stream.of(sortOption, sizeOption, cursorOption)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }
}
