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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.thingsearch.api.commands.sudo.StreamThings;
import org.eclipse.ditto.thingsearch.model.CursorOption;
import org.eclipse.ditto.thingsearch.model.LimitOption;
import org.eclipse.ditto.thingsearch.model.Option;
import org.eclipse.ditto.thingsearch.model.SearchResult;
import org.eclipse.ditto.thingsearch.model.SearchResultBuilder;
import org.eclipse.ditto.thingsearch.model.SizeOption;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.JsonToBson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.coding.Coder;
import akka.japi.pf.PFBuilder;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import scala.PartialFunction;

/**
 * Package-private evaluator and generator of opaque cursors.
 * The meaning of a cursor should be invisible to all users.
 * This class provides the following services to other classes of this package:
 * <ul>
 * <li>{@code extractCursor(QueryThings, ActorSystem)}:
 * Read any cursor given by a {@code QueryThings} command.
 * </li>
 * <li>{@code adjust(Optional<ThingsSearchCursor>, QueryThings)}:
 * Augment a {@code QueryThings} command by information in the cursor.
 * </li>
 * <li>{@code adjust(Optional<ThingsSearchCursor>, Query, CriteriaFactory)}:
 * Adjust a {@code Query} so that its results start from the location marked by the cursor.
 * </li>
 * <li>{@code processSearchResult(QueryThings, ThingsSearchCursor, SearchResult, ResultList)}:
 * Compute a cursor pointing at the end of the search result if there are more results.
 * </li>
 * </ul>
 */
final class ThingsSearchCursor {

    private static final Logger LOG = LoggerFactory.getLogger(ThingsSearchCursor.class);

    static final SortOptionEntry DEFAULT_SORT_OPTION_ENTRY =
            SortOptionEntry.asc(Thing.JsonFields.ID.getPointer());

    private static final String LIMIT_OPTION_FORBIDDEN = "The options 'cursor' and 'limit' must not be used together.";

    private static final Base64.Encoder BASE64_URL_ENCODER_WITHOUT_PADDING = Base64.getUrlEncoder().withoutPadding();
    private static final PartialFunction<Throwable, Throwable> DECODE_ERROR_MAPPER = createDecodeErrorMapper();

    /*
     * Secret fields in JSON representation of a cursor.
     */

    private static final JsonFieldDefinition<String> FILTER = JsonFactory.newStringFieldDefinition("F");
    private static final JsonFieldDefinition<JsonArray> NAMESPACES = JsonFactory.newJsonArrayFieldDefinition("N");
    private static final JsonFieldDefinition<String> CORRELATION_ID = JsonFactory.newStringFieldDefinition("C");
    private static final JsonFieldDefinition<JsonArray> VALUES = JsonFactory.newJsonArrayFieldDefinition("V");
    private static final JsonFieldDefinition<String> SORT_OPTION = JsonFactory.newStringFieldDefinition("S");

    /*
     * Data encoded in a cursor.
     */

    @Nullable private final String filter;
    @Nullable private final Set<String> namespaces;
    @Nullable final String correlationId;
    private final SortOption sortOption;
    private final JsonArray values;

    ThingsSearchCursor(@Nullable final Set<String> namespaces, @Nullable final String correlationId,
            final SortOption sortOption, @Nullable final String filter, final JsonArray values) {
        this.namespaces = namespaces;
        this.filter = filter;

        this.correlationId = correlationId;
        this.sortOption = sortOption;
        this.values = values;

        if (sortOption.getSize() != values.getSize()) {
            // Cursor corrupted. Offer no more information.
            throw invalidCursorBuilder().build();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + toJson();
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, namespaces, correlationId, sortOption, values);
    }

    @Override
    public boolean equals(final Object that) {
        if (that instanceof ThingsSearchCursor c) {
            return Arrays.asList(filter, namespaces, correlationId, sortOption, values)
                    .equals(Arrays.asList(c.filter, c.namespaces, c.correlationId, c.sortOption, c.values));
        } else {
            return false;
        }
    }

    /**
     * Log the correlation ID of the query that generated the cursor.
     *
     * @param log the logger.
     */
    void logCursorCorrelationId(final ThreadSafeDittoLoggingAdapter log) {
        log.info("CursorCorrelationId = {}", correlationId);
    }

    /**
     * Check whether this cursor is valid for a {@code QueryThings} command.
     * A cursor is compatible with a command if
     * <ul>
     * <li>their filter strings are identical,</li>
     * <li>their sort options are compatible, and</li>
     * <li>the command has no limit option.</li>
     * </ul>
     *
     * @param queryThings the command.
     * @param commandOptions parsed options of the command.
     * @return reason why this cursor is invalid for the command.
     */
    private Optional<DittoRuntimeException> checkCursorValidity(final QueryThings queryThings,
            final List<Option> commandOptions) {

        // when a cursor is present, the command may only have an additional size option and a field selector.
        final boolean commandHasDifferentFilter =
                queryThings.getFilter().filter(f -> !Objects.equals(f, filter)).isPresent();

        final String description;

        if (commandHasDifferentFilter) {
            description = "The parameter 'filter' must not differ from the original query of the cursor.";
        } else if (commandOptions.stream().anyMatch(LimitOption.class::isInstance)) {
            description = LIMIT_OPTION_FORBIDDEN;
        } else if (hasIncompatibleSortOption(commandOptions)) {
            description = "The option 'sort' must not differ from the original query of the cursor.";
        } else {
            description = null;
        }

        return Optional.ofNullable(description).map(d -> invalidCursor(d, queryThings));
    }

    /**
     * Check if options from a {@code QueryThings} command contains an incompatible sort option.
     * A sort option is compatible with the sort option of this cursor if
     * <ul>
     * <li>both are identical,</li>
     * <li>cursor option is obtained from command option by appending the default option, or</li>
     * <li>cursor option is obtained from command option by truncating after +thingId or -thingId.</li>
     * </ul>
     *
     * @param commandOptions options from a command.
     * @return whether the options contains an incompatible sort option.
     */
    private boolean hasIncompatibleSortOption(final List<Option> commandOptions) {
        return findAll(SortOption.class, commandOptions).stream()
                .anyMatch(sortOption -> !areCompatible(this.sortOption.getEntries(), sortOption.getEntries(), 0));
    }

    /**
     * During paging, augment a search result by a cursor pointing at its end.
     *
     * @param searchResult the search result.
     * @param resultList items in the search result.
     * @return search result augmented by a new cursor.
     */
    private SearchResult searchResultWithExistingCursor(final SearchResult searchResult,
            final ResultList<?> resultList) {
        final Optional<JsonArray> newValues = resultList.lastResultSortValues();
        if (newValues.isPresent()) {
            final ThingsSearchCursor newCursor =
                    new ThingsSearchCursor(namespaces, correlationId, sortOption, filter, newValues.get());
            return searchResult.toBuilder()
                    .cursor(newCursor.encode())
                    .nextPageOffset(null)
                    .build();
        } else {
            return searchResult.toBuilder().nextPageOffset(null).build();
        }
    }

    /**
     * @return Compute a {@code QueryThings} using content of this cursor.
     */
    private QueryThings adjustQueryThings(final QueryThings queryThings) {
        final List<String> adjustedOptions =
                Stream.concat(
                        Stream.of(RqlOptionParser.unparse(Collections.singletonList(sortOption))),
                        queryThings.getOptions()
                                .stream()
                                .flatMap(Collection::stream)
                                .filter(option -> !option.startsWith("sort")))
                        .collect(Collectors.toList());
        // leave correlation ID alone
        final DittoHeaders headers = queryThings.getDittoHeaders();
        return QueryThings.of(filter, adjustedOptions, queryThings.getFields().orElse(null), namespaces, headers);
    }

    /**
     * Adjust a {@code Query} object so that its results start at the location of this cursor.
     *
     * @param query the query object.
     * @param cf a criteria factory.
     * @return a new query object starting at the location of this cursor.
     */
    private Query adjustQuery(final Query query, final CriteriaFactory cf) {
        return query.withCriteria(cf.and(Arrays.asList(query.getCriteria(),
                getNextPageFilter(query.getSortOptions(), values, cf))));
    }

    /**
     * @return Secret JSON representation of this cursor.
     */
    private JsonObject toJson() {
        final java.util.function.Predicate<JsonField> notNull = field -> !field.getValue().isNull();
        return JsonFactory.newObjectBuilder()
                .set(FILTER, filter, notNull)
                .set(NAMESPACES, renderNamespaces(), notNull)
                .set(CORRELATION_ID, correlationId, notNull)
                .set(SORT_OPTION, RqlOptionParser.unparse(Collections.singletonList(sortOption)))
                .set(VALUES, values)
                .build();
    }

    /**
     * @return namespaces of this cursor as JSON array.
     */
    @Nullable
    private JsonArray renderNamespaces() {
        if (namespaces != null) {
            return namespaces.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
        } else {
            return null;
        }
    }

    /**
     * @return Coded string representation of this cursor.
     */
    String encode() {
        final ByteString byteString = ByteString.fromString(toJson().toString(), StandardCharsets.UTF_8);
        final ByteString compressed = Coder.Deflate.encode(byteString);
        return BASE64_URL_ENCODER_WITHOUT_PADDING.encodeToString(compressed.toByteBuffer().array());
    }


    /**
     * Decode the string representation of a cursor.
     *
     * @param cursorString the string representation.
     * @param system actor system holding the default materializer
     * @return source of the decoded cursor or a failed source containing a {@code DittoRuntimeException}.
     */
    static Source<ThingsSearchCursor, NotUsed> decode(final String cursorString, final ActorSystem system) {
        return Source.fromCompletionStage(decodeCS(cursorString, system)).mapError(DECODE_ERROR_MAPPER);
    }

    private static CompletionStage<ThingsSearchCursor> decodeCS(final String cursorString,
            final ActorSystem system) {
        final ByteString compressed = ByteString.fromArray(Base64.getUrlDecoder().decode(cursorString));
        return Coder.Deflate.decode(compressed, SystemMaterializer.get(system).materializer())
                .thenApply(decompressed -> fromJson(JsonFactory.newObject(decompressed.utf8String())));
    }

    private static PartialFunction<Throwable, Throwable> createDecodeErrorMapper() {
        // offer no explanation for non-decodable cursors.
        return new PFBuilder<Throwable, Throwable>()
                .matchAny(error -> {
                    final Throwable e = error instanceof CompletionException ? error.getCause() : error;
                    LOG.info("Failed to decode cursor: {} '{}' due to {}", e.getClass(), e.getMessage(),
                            Objects.toString(e.getCause()));
                    return invalidCursorBuilder().build();
                })
                .build();
    }

    /**
     * Adjust a {@code QueryThings} by the content of an optional cursor.
     *
     * @param cursor an optional cursor.
     * @param queryThings the command to adjust.
     * @return the adjusted command if the cursor exists; the unadjusted command if the cursor does not exist.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static QueryThings adjust(final Optional<ThingsSearchCursor> cursor, final QueryThings queryThings) {
        return cursor.map(c -> c.adjustQueryThings(queryThings)).orElse(queryThings);
    }

    /**
     * Adjust a {@code Query} object so that its result starts with the location of an optional cursor.
     *
     * @param cursor an optional cursor.
     * @param query the query to adjust.
     * @param cf a criteria factory.
     * @return the adjusted {@code Query} if the cursor exists; the unadjusted {@code Query} if the cursor does not
     * exist.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static Query adjust(final Optional<ThingsSearchCursor> cursor, final Query query, final CriteriaFactory cf) {
        return cursor.map(c -> c.adjustQuery(query, cf)).orElse(query);
    }

    /**
     * Extract a cursor from a {@code QueryThings} command if any exists.
     *
     * @param queryThings the command.
     * @param system actor system through which to access the materializer.
     * @return source of an optional cursor if the command has no cursor or has a valid cursor; a failed source if the
     * command has an invalid cursor.
     */
    static Source<Optional<ThingsSearchCursor>, NotUsed> extractCursor(final QueryThings queryThings,
            final ActorSystem system) {

        return catchExceptions(queryThings.getDittoHeaders(), () -> {
            final List<Option> options = getOptions(queryThings);
            final List<CursorOption> cursorOptions = findAll(CursorOption.class, options);
            final List<LimitOption> limitOptions = findAll(LimitOption.class, options);
            final Optional<InvalidOptionException> sizeOptionError = checkSizeOption(options, queryThings);
            if (sizeOptionError.isPresent()) {
                return Source.failed(sizeOptionError.get());
            } else if (cursorOptions.isEmpty()) {
                return Source.single(Optional.empty());
            } else if (cursorOptions.size() > 1) {
                // there may not be 2 or more cursor options in 1 command.
                return Source.failed(invalidCursor("There may not be more than 1 'cursor' option.", queryThings));
            } else if (!limitOptions.isEmpty()) {
                return Source.failed(invalidCursor(LIMIT_OPTION_FORBIDDEN, queryThings));
            } else {
                return decode(cursorOptions.get(0).getCursor(), system)
                        .flatMapConcat(cursor -> cursor.checkCursorValidity(queryThings, options)
                                .<Source<Optional<ThingsSearchCursor>, NotUsed>>map(Source::failed)
                                .orElse(Source.single(Optional.of(cursor))));
            }
        });
    }

    static Source<Optional<ThingsSearchCursor>, NotUsed> extractCursor(final StreamThings streamThings) {
        return catchExceptions(streamThings.getDittoHeaders(), () -> {
            final Optional<JsonArray> sortValuesOptional = streamThings.getSortValues();
            if (sortValuesOptional.isEmpty()) {
                return Source.single(Optional.empty());
            }
            final JsonArray sortValues = sortValuesOptional.get();
            final List<Option> options = streamThings.getSort().map(RqlOptionParser::parseOptions).orElseGet(List::of);
            final SortOption sortOption = findUniqueSortOption(options);
            if (sortValues.getSize() != sortOption.getSize()) {
                return Source.failed(
                        invalidCursor("sort option and sort values have different dimensions", streamThings));
            }
            final ThingsSearchCursor cursor = new ThingsSearchCursor(streamThings.getNamespaces().orElse(null),
                    streamThings.getDittoHeaders().getCorrelationId().orElse(null),
                    sortOption,
                    streamThings.getFilter().orElse(null),
                    sortValues
            );
            return Source.single(Optional.of(cursor));
        });
    }

    private static <T> Source<T, NotUsed> catchExceptions(final DittoHeaders dittoHeaders,
            final Supplier<Source<T, NotUsed>> sourceSupplier) {
        try {
            return sourceSupplier.get();
        } catch (final ParserException | IllegalArgumentException e) {
            return Source.failed(InvalidRqlExpressionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build());
        } catch (final Throwable error) {
            return Source.failed(error);
        }
    }

    /**
     * Augment a search result by the next cursor as needed.
     *
     * @param queryThings the command that produced the results.
     * @param cursor cursor given by the command, if any.
     * @param searchResult the search result.
     * @param resultList items in the search result.
     * @return search result with cursor or next-page-offset or both as appropriate.
     */
    static SearchResult processSearchResult(final QueryThings queryThings,
            @Nullable final ThingsSearchCursor cursor,
            final SearchResult searchResult,
            final ResultList<?> resultList) {

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

    /**
     * Locate instances of a class within a collection.
     *
     * @param clazz the class.
     * @param collection the collection.
     * @param <T> type of the class.
     * @return list of all instances of the class in the collection.
     */
    private static <T> List<T> findAll(final Class<T> clazz, final Collection<?> collection) {
        return collection.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.toList());
    }

    /**
     * Find a unique sort option within a list of options of a cursor. If none exists, then the cursor is corrupted and
     * an exception is thrown offering no insight into the cursor's composition.
     *
     * @param options list of options of a cursor.
     * @return the unique sort option.
     * @throws org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException if the list contains 0
     * or more than 1 sort option.
     */
    private static SortOption findUniqueSortOption(final List<Option> options) {
        final List<SortOption> sortOptions = findAll(SortOption.class, options);
        if (sortOptions.size() == 1) {
            return sortOptions.get(0);
        } else {
            // Cursor corrupted. Offer no more information.
            throw invalidCursorBuilder().build();
        }
    }

    /**
     * Create a builder for errors due to invalid cursors.
     *
     * @return the exception builder.
     */
    private static DittoRuntimeExceptionBuilder<InvalidOptionException> invalidCursorBuilder() {
        return InvalidOptionException.newBuilder()
                .message("The option 'cursor' is not valid for the search request.");
    }

    /**
     * Create an exception due to an invalid cursor.
     *
     * @param description why the cursor is invalid.
     * @param withDittoHeaders signal whose headers the exception should retain.
     * @return the exception.
     */
    private static InvalidOptionException invalidCursor(final String description,
            final WithDittoHeaders withDittoHeaders) {

        return invalidCursor(description, withDittoHeaders.getDittoHeaders());
    }

    /**
     * Create an exception due to an invalid cursor.
     *
     * @param description why the cursor is invalid.
     * @param dittoHeaders headers of the exception.
     * @return the exception.
     */
    private static InvalidOptionException invalidCursor(final String description,
            final DittoHeaders dittoHeaders) {

        return invalidCursorBuilder().description(description)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Create a cursor from its secret JSON representation.
     *
     * @param json the JSON representation.
     * @return the cursor.
     */
    static ThingsSearchCursor fromJson(final JsonObject json) {
        return new ThingsSearchCursor(
                json.getValue(NAMESPACES).map(ThingsSearchCursor::readNamespaces).orElse(null),
                json.getValue(CORRELATION_ID).orElse(null),
                findUniqueSortOption(RqlOptionParser.parseOptions(json.getValueOrThrow(SORT_OPTION))),
                json.getValue(FILTER).orElse(null),
                json.getValueOrThrow(VALUES));
    }

    /**
     * Read a set of namespaces from a JSON array.
     *
     * @param array the array.
     * @return set of namespaces.
     */
    private static Set<String> readNamespaces(final JsonArray array) {
        return array.stream().map(JsonValue::asString).collect(Collectors.toSet());
    }

    /**
     * Parse options of a {@code QueryThings} command.
     *
     * @param queryThings the command.
     * @return parsed options.
     */
    private static List<Option> getOptions(final QueryThings queryThings) {
        return queryThings.getOptions()
                .map(options -> String.join(",", options))
                .map(RqlOptionParser::parseOptions)
                .orElse(Collections.emptyList());
    }

    /**
     * Augment a fresh search result (i. e., not obtained via any cursor) by a new cursor if appropriate.
     *
     * @param queryThings the command that produced the search result.
     * @param searchResult the search result.
     * @param resultList items in the search result.
     * @return the augmented search result.
     */
    private static SearchResult searchResultWithNewCursor(final QueryThings queryThings,
            final SearchResult searchResult, final ResultList<?> resultList) {

        final List<Option> commandOptions = getOptions(queryThings);
        final boolean hasLimitOption = !findAll(LimitOption.class, commandOptions).isEmpty();
        final boolean hasSizeOption = !findAll(SizeOption.class, commandOptions).isEmpty();

        if (hasNextPage(resultList)) {
            // there are more results; append cursor and offset as appropriate
            final SearchResultBuilder builder = searchResult.toBuilder();

            if (hasLimitOption) {
                // limit option is present. Do not compute cursor.
                builder.cursor(null);
            } else {
                // limit option is absent. Compute cursor.
                final ThingsSearchCursor newCursor = computeNewCursor(queryThings, resultList);
                builder.cursor(newCursor.encode());

                // size option is present. Remove next-page-offset.
                if (hasSizeOption) {
                    // using size option; do not deliver nextPageOffset
                    builder.nextPageOffset(null);
                }
            }
            return builder.build();
        } else if (hasSizeOption) {
            // This is the last page. Size option is present. Remove next-page-offset.
            return searchResult.toBuilder().nextPageOffset(null).build();
        } else {
            // This is the last page. Size option is absent. Retain next-page-offset.
            return searchResult;
        }
    }

    /**
     * Compute cursor for a {@code QueryThings} without cursor.
     *
     * @param queryThings the command.
     * @param resultList search result produced by the command.
     * @return cursor at the end of the search result.
     */
    private static ThingsSearchCursor computeNewCursor(final QueryThings queryThings, final ResultList<?> resultList) {

        return new ThingsSearchCursor(queryThings.getNamespaces().orElse(null),
                queryThings.getDittoHeaders().getCorrelationId().orElse(null),
                sortOptionForNewCursor(queryThings),
                queryThings.getFilter().orElse(null),
                resultList.lastResultSortValues().orElse(JsonArray.empty()));
    }

    /**
     * Compute the sort option for a new cursor such that at least one dimension is non-null for all things.
     *
     * @param queryThings the command.
     * @return the sort option for the new cursor.
     */
    private static SortOption sortOptionForNewCursor(final QueryThings queryThings) {
        final List<SortOption> sortOptions = findAll(SortOption.class, getOptions(queryThings));
        final List<SortOptionEntry> entries =
                sortOptions.isEmpty() ? Collections.emptyList() : sortOptions.get(0).getEntries();
        return SortOption.of(ensureDefaultPropertyPath(entries));
    }

    /**
     * Append default sort entry if a command's sort option does not include thing ID.
     *
     * @param entries sort option entries of a command.
     * @return augmented sort option entries.
     */
    private static List<SortOptionEntry> ensureDefaultPropertyPath(final List<SortOptionEntry> entries) {
        final JsonPointer defaultPropertyPath = DEFAULT_SORT_OPTION_ENTRY.getPropertyPath();
        final OptionalInt thingIdEntry = IntStream.range(0, entries.size())
                .filter(i -> Objects.equals(defaultPropertyPath, entries.get(i).getPropertyPath()))
                .findFirst();
        if (thingIdEntry.isPresent()) {
            return entries.subList(0, thingIdEntry.getAsInt() + 1);
        } else {
            final List<SortOptionEntry> augmentedEntries = new ArrayList<>(entries);
            augmentedEntries.add(DEFAULT_SORT_OPTION_ENTRY);
            return augmentedEntries;
        }
    }

    /**
     * Filter out results before a cursor's position.
     *
     * @param sortOptions sort options of the parsed query.
     * @param previousValues values of the fields in the sort options of a cursor marking its position.
     * @param cf a criteria factory.
     * @return criteria to filter out results before a cursor's position.
     */
    private static Criteria getNextPageFilter(final List<org.eclipse.ditto.rql.query.SortOption> sortOptions,
            final JsonArray previousValues,
            final CriteriaFactory cf) {

        if (sortOptions.size() != previousValues.getSize()) {
            // this should not happen.
            throw invalidCursorBuilder().build();
        }
        return getNextPageFilterImpl(sortOptions, previousValues, cf, 0);
    }

    /**
     * Recursive implementation of {@code getNextPageFilter}.
     *
     * @param sortOptionEntries sort options of the parsed query.
     * @param previousValues values of the fields in the sort options of a cursor marking its position.
     * @param cf a criteria factory.
     * @param i dimension to start generating criteria for.
     * @return criteria starting from the ith dimension.
     */
    private static Criteria getNextPageFilterImpl(
            final List<org.eclipse.ditto.rql.query.SortOption> sortOptionEntries,
            final JsonArray previousValues,
            final CriteriaFactory cf, final int i) {

        final org.eclipse.ditto.rql.query.SortOption sortOption = sortOptionEntries.get(i);
        final JsonValue previousValue = previousValues.get(i).orElse(JsonFactory.nullLiteral());
        final Criteria ithDimensionCriteria = getDimensionLtCriteria(sortOption, previousValue, cf);
        if (i + 1 >= sortOptionEntries.size()) {
            return ithDimensionCriteria;
        } else {
            final Criteria nextDimension = getNextPageFilterImpl(sortOptionEntries, previousValues, cf, i + 1);
            return getNextDimensionCriteria(ithDimensionCriteria, nextDimension, sortOption, previousValue, cf);
        }
    }

    /**
     * Generate a criteria to filter for things whose value on a field prior to a cursor's position according to
     * the ordering specified by a sort option.
     *
     * @param entry sort option specifying an ordering on a field.
     * @param previousValue value of the field in the sort option marking the position of a cursor.
     * @param cf a criteria factory.
     * @return criteria to filter for things prior to a cursor's position on the specified field.
     */
    private static Criteria getDimensionLtCriteria(final org.eclipse.ditto.rql.query.SortOption entry,
            final JsonValue previousValue, final CriteriaFactory cf) {

        // special handling for null values needed due to comparison operators never matching null values
        if (entry.getSortDirection() == SortDirection.ASC) {
            if (previousValue.isNull()) {
                // ASC null: any value is bigger than null
                return cf.existsCriteria(entry.getSortExpression());
            } else {
                // ASC nonnull: null values cannot be bigger and can be ignored
                return cf.fieldCriteria(entry.getSortExpression(), cf.gt(JsonToBson.convert(previousValue)));
            }
        } else {
            if (previousValue.isNull()) {
                // DESC null: smaller than null means false
                return cf.nor(cf.any());
            } else {
                // DESC nonnull: null is smaller than any value
                return cf.or(Arrays.asList(
                        cf.fieldCriteria(entry.getSortExpression(), cf.lt(JsonToBson.convert(previousValue))),
                        cf.nor(cf.existsCriteria(entry.getSortExpression()))
                ));
            }
        }
    }

    /**
     * Generate a criteria to filter for things that precede the cursor's position due to this dimension or subsequent
     * dimensions taking null values into account.
     *
     * @param thisDimensionLt criteria to filter for things prior to the cursor's position on this dimension.
     * @param nextDimension criteria to filter for things prior to the cursor's position on subsequent dimensions.
     * @param sortOption parsed sort option for this dimension.
     * @param previousValue value on this dimension marking the position of the cursor.
     * @param cf a criteria factory.
     * @return criteria to filter for things that precede the cursor's position due to this dimension or subsequent
     * dimensions.
     */
    private static Criteria getNextDimensionCriteria(final Criteria thisDimensionLt, final Criteria nextDimension,
            final org.eclipse.ditto.rql.query.SortOption sortOption, final JsonValue previousValue,
            final CriteriaFactory cf) {

        final Criteria thisDimensionEq;
        if (previousValue.isNull()) {
            thisDimensionEq = cf.or(Arrays.asList(
                    cf.nor(cf.existsCriteria(sortOption.getSortExpression())),
                    cf.fieldCriteria(sortOption.getSortExpression(), cf.eq(null))
            ));
        } else {
            thisDimensionEq =
                    cf.fieldCriteria(sortOption.getSortExpression(), cf.eq(JsonToBson.convert(previousValue)));
        }
        return cf.or(Arrays.asList(thisDimensionLt, cf.and(Arrays.asList(thisDimensionEq, nextDimension))));
    }

    /**
     * Test whether there are more results.
     *
     * @param resultList items of a search result.
     * @return whether there are more results.
     */
    private static boolean hasNextPage(final ResultList<?> resultList) {
        return resultList.lastResultSortValues().isPresent();
    }

    /**
     * Test if sort options of a cursor is compatible with sort options of a command starting from a dimension.
     *
     * @param cursorSortOptionEntries sort options of a cursor.
     * @param commandSortOptionEntries sort options of a command.
     * @param i the dimension to start the check from.
     * @return whether the sort options are compatible.
     */
    private static boolean areCompatible(final List<SortOptionEntry> cursorSortOptionEntries,
            final List<SortOptionEntry> commandSortOptionEntries,
            final int i) {

        if (i >= cursorSortOptionEntries.size() && i >= commandSortOptionEntries.size()) {
            // all dimensions checked; sort options are compatible.
            return true;
        } else if (i >= commandSortOptionEntries.size()) {
            // command sort options have fewer dimensions; ensure cursor sort options are obtained by appending default.
            return i + 1 == cursorSortOptionEntries.size() &&
                    DEFAULT_SORT_OPTION_ENTRY.equals(cursorSortOptionEntries.get(i));
        } else if (i >= cursorSortOptionEntries.size()) {
            // cursor sort options have fewer dimensions; incompatible.
            return false;
        } else {
            // check this dimension has equal sort option entries and recurse onto subsequent dimensions
            final SortOptionEntry cursorEntry = cursorSortOptionEntries.get(i);
            final boolean isThisDimensionEq =
                    Objects.equals(cursorEntry, commandSortOptionEntries.get(i));

            if (isThisDimensionEq) {
                final boolean isThisDimensionBijective =
                        Objects.equals(DEFAULT_SORT_OPTION_ENTRY.getPropertyPath(), cursorEntry.getPropertyPath());
                return isThisDimensionBijective ||
                        areCompatible(cursorSortOptionEntries, commandSortOptionEntries, i + 1);
            } else {
                return false;
            }
        }
    }

    /**
     * Check that at most 1 size option is given and its argument is positive.
     *
     * @param options options of a {@code QueryThings} command.
     * @param withDittoHeaders headers of the command.
     * @return empty optional if any size option is valid, or the reason why they are not.
     */
    private static Optional<InvalidOptionException> checkSizeOption(final List<Option> options,
            final WithDittoHeaders withDittoHeaders) {

        final List<SizeOption> sizeOptions = findAll(SizeOption.class, options);
        if (sizeOptions.size() > 1) {
            return Optional.of(invalidCursorBuilder()
                    .message("There may not be more than 1 'size' option.")
                    .dittoHeaders(withDittoHeaders.getDittoHeaders())
                    .build());
        } else if (!sizeOptions.isEmpty() && sizeOptions.get(0).getSize() <= 0) {
            return Optional.of(invalidCursorBuilder()
                    .message("The option 'size' must be a positive integer.")
                    .dittoHeaders(withDittoHeaders.getDittoHeaders())
                    .build());
        } else {
            return Optional.empty();
        }
    }
}
