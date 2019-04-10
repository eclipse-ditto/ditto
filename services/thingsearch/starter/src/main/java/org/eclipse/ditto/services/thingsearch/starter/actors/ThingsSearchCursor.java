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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.expression.FilterFieldExpression;
import org.eclipse.ditto.model.query.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.CursorOption;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.OptionVisitor;
import org.eclipse.ditto.model.thingsearch.SearchResult;
import org.eclipse.ditto.model.thingsearch.SearchResultBuilder;
import org.eclipse.ditto.model.thingsearch.SizeOption;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.eclipse.ditto.model.thingsearchparser.RqlOptionParser;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;

import akka.NotUsed;
import akka.http.javadsl.coding.Coder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * Package-private evaluator and generator of opaque cursors.
 */
// TODO: document and test.
final class ThingsSearchCursor {

    private static final SortOptionEntry DEFAULT_SORT_OPTION_ENTRY =
            SortOptionEntry.asc(Thing.JsonFields.ID.getPointer());

    private static final Base64.Encoder BASE64_URL_ENCODER_WITHOUT_PADDING = Base64.getUrlEncoder().withoutPadding();

    // secret fields in JSON representation
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
        final List<Option> newOptions =
                new OptionsBuilder().visitAll(options).visitAll(inputOptions).withSortOption(sortOption).build();
        final DittoHeaders newHeaders = dittoHeaders.toBuilder().putHeaders(queryThings.getDittoHeaders()).build();
        return new ThingsSearchCursor(newSelector, namespaces, newHeaders, newOptions, filter, values);
    }

    private boolean hasForbiddenFields(final QueryThings queryThings, final List<Option> commandOptions) {
        // when a cursor is present, the command may only have an additional size option and a field selector.
        final boolean commandHasDifferentFilter =
                queryThings.getFilter().filter(f -> !Objects.equals(f, filter)).isPresent();
        return commandHasDifferentFilter ||
                commandOptions.stream().anyMatch(LimitOption.class::isInstance) ||
                hasIncompatibleSortOption(commandOptions);
    }

    private boolean hasIncompatibleSortOption(final List<Option> commandOptions) {
        return findAll(SortOption.class, commandOptions).stream()
                .anyMatch(sortOption -> !areCompatible(this.sortOption.getEntries(), sortOption.getEntries(), 0));
    }

    private SearchResult searchResultWithExistingCursor(final SearchResult searchResult,
            final ResultList<?> resultList) {
        final Optional<JsonArray> newValues = resultList.lastResultSortValues();
        if (newValues.isPresent()) {
            final ThingsSearchCursor newCursor =
                    new ThingsSearchCursor(jsonFieldSelector, namespaces, dittoHeaders, options, filter,
                            newValues.get());
            return searchResult.toBuilder()
                    .cursor(newCursor.encode())
                    .nextPageOffset(null)
                    .build();
        } else {
            return searchResult.toBuilder().nextPageOffset(null).build();
        }
    }

    QueryThings toQueryThings(final CriteriaFactory cf) {
        final List<String> optionStrings = options.stream().map(Option::toString).collect(Collectors.toList());
        final JsonFieldSelector selector = Optional.ofNullable(jsonFieldSelector)
                .map(JsonFieldSelector::newInstance)
                .orElse(null);
        return QueryThings.of(computeFilterString(cf), optionStrings, selector, namespaces, dittoHeaders);
    }

    private String encode() {
        final ByteString byteString = ByteString.fromString(toJson().toString(), StandardCharsets.UTF_8);
        final ByteString compressed = Coder.Deflate.encode(byteString);
        return BASE64_URL_ENCODER_WITHOUT_PADDING.encodeToString(compressed.toByteBuffer().array());
    }

    // TODO: test decode/encode being inverse of each other
    static CompletionStage<ThingsSearchCursor> decode(final String cursorString,
            final ActorMaterializer materializer) {
        final ByteString compressed = ByteString.fromArray(Base64.getUrlDecoder().decode(cursorString));
        return Coder.Deflate.decode(compressed, materializer)
                .thenApply(decompressed -> fromJson(JsonFactory.newObject(decompressed.utf8String())));
    }

    static Source<Optional<ThingsSearchCursor>, NotUsed> extractCursor(final QueryThings queryThings,
            final ActorMaterializer materializer) {

        try {
            final List<Option> options = getOptions(queryThings);
            final List<CursorOption> cursorOptions = findAll(CursorOption.class, options);
            final List<LimitOption> limitOptions = findAll(LimitOption.class, options);
            if (cursorOptions.isEmpty()) {
                return Source.single(Optional.empty());
            } else if (limitOptions.isEmpty() && cursorOptions.size() == 1) {
                return Source.fromCompletionStage(decode(cursorOptions.get(0).getCursor(), materializer))
                        .map(cursor -> Optional.of(cursor.override(queryThings, options)));
            } else {
                // there may not be 2 or more cursor options in 1 command.
                return Source.failed(invalidCursor());
            }
        } catch (final Throwable error) {
            return Source.failed(error);
        }
    }

    static SearchResult processSearchResult(final QueryThings queryThings,
            @Nullable final ThingsSearchCursor cursor,
            final SearchResult searchResult,
            final ResultList<String> resultList) {

        if (!findAll(LimitOption.class, getOptions(queryThings)).isEmpty()) {
            // do not deliver cursor if "limit" is specified
            return searchResult;
        } else if (cursor != null) {
            // adjust next cursor by search result, do not deliver nextPageOffset
            return cursor.searchResultWithExistingCursor(searchResult, resultList);
        } else {
            // compute new cursor, deliver both
            return searchResultWithNewCursor(queryThings, searchResult, resultList);
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
                json.getValue(JSON_FIELD_SELECTOR).orElse(null),
                null,
                json.getValue(CORRELATION_ID).map(ThingsSearchCursor::correlationIdHeader).orElse(DittoHeaders.empty()),
                RqlOptionParser.parseOptions(json.getValueOrThrow(OPTIONS)),
                json.getValue(FILTER).orElse(null),
                json.getValueOrThrow(VALUES));
    }

    private static List<Option> getOptions(final QueryThings queryThings) {
        return queryThings.getOptions()
                .map(options -> String.join(",", options))
                .map(RqlOptionParser::parseOptions)
                .orElse(Collections.emptyList());
    }

    // TODO: remove if it does not work
    private static DittoHeaders correlationIdHeader(final String correlationId) {
        return DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .build();
    }

    private static SearchResult searchResultWithNewCursor(final QueryThings queryThings,
            final SearchResult searchResult, final ResultList<?> resultList) {

        if (hasNextPage(resultList)) {
            final ThingsSearchCursor newCursor = computeNewCursor(queryThings, resultList);
            final SearchResultBuilder builder = searchResult.toBuilder().cursor(newCursor.encode());
            if (!findAll(SizeOption.class, newCursor.options).isEmpty()) {
                // using size option; do not deliver nextPageOffset
                builder.nextPageOffset(null);
            }
            return builder.build();
        } else {
            return searchResult;
        }
    }

    private static ThingsSearchCursor computeNewCursor(final QueryThings queryThings, final ResultList<?> resultList) {

        return new ThingsSearchCursor(queryThings.getFields().map(JsonFieldSelector::toString).orElse(null),
                queryThings.getNamespaces().orElse(null),
                queryThings.getDittoHeaders(),
                mergeOptionsForNewCursor(queryThings),
                queryThings.getFilter().orElse(null),
                resultList.lastResultSortValues().orElse(JsonArray.empty()));
    }

    private static List<Option> mergeOptionsForNewCursor(final QueryThings queryThings) {
        // override sort option from command by those from the query--the latter has at least 1 non-null dimension
        return new OptionsBuilder().visitAll(getOptions(queryThings))
                .withSortOption(sortOptionForNewCursor(queryThings))
                .build();
    }

    private static SortOption sortOptionForNewCursor(final QueryThings queryThings) {
        final List<SortOption> sortOptions = findAll(SortOption.class, getOptions(queryThings));
        final List<SortOptionEntry> entries =
                sortOptions.isEmpty() ? Collections.emptyList() : sortOptions.get(0).getEntries();
        return SortOption.of(ensureDefaultPropertyPath(entries));
    }

    private static List<SortOptionEntry> ensureDefaultPropertyPath(final List<SortOptionEntry> entries) {
        final JsonPointer defaultPropertyPath = DEFAULT_SORT_OPTION_ENTRY.getPropertyPath();
        final boolean hasThingIdEntry = entries.stream()
                .anyMatch(entry -> Objects.equals(defaultPropertyPath, entry.getPropertyPath()));
        if (hasThingIdEntry) {
            return entries;
        } else {
            final List<SortOptionEntry> augmentedEntries = new ArrayList<>(entries);
            augmentedEntries.add(DEFAULT_SORT_OPTION_ENTRY);
            return augmentedEntries;
        }
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

    private static boolean hasNextPage(final ResultList<?> resultList) {
        return resultList.lastResultSortValues().isPresent();
    }

    private static boolean areCompatible(final List<SortOptionEntry> sortOptionEntries,
            final List<SortOptionEntry> commandSortOptionEntries,
            final int i) {
        if (i >= sortOptionEntries.size()) {
            return true;
        } else if (i >= commandSortOptionEntries.size()) {
            return i + 1 == sortOptionEntries.size() && DEFAULT_SORT_OPTION_ENTRY.equals(sortOptionEntries.get(i));
        } else {
            return Objects.equals(sortOptionEntries.get(i), commandSortOptionEntries.get(i)) &&
                    areCompatible(sortOptionEntries, commandSortOptionEntries, i + 1);
        }
    }

    private static final class OptionsBuilder implements OptionVisitor {

        @Nullable private SortOption sortOption;
        @Nullable private SizeOption sizeOption;

        private OptionsBuilder visitAll(final List<Option> startingOptions) {
            startingOptions.forEach(option -> option.accept(this));
            return this;
        }

        private OptionsBuilder withSortOption(final SortOption sortOption) {
            this.sortOption = sortOption;
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
            // do nothing; cursor options are ignored
        }

        @Override
        public void visit(final SizeOption sizeOption) {
            this.sizeOption = sizeOption;
        }

        private List<Option> build() {
            return Stream.of(sortOption, sizeOption)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }
}
