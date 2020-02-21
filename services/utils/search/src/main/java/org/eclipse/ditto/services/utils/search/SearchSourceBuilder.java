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
package org.eclipse.ditto.services.utils.search;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.model.rql.ParserException;
import org.eclipse.ditto.model.rqlparser.RqlPredicateParser;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.thingsearch.Option;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.eclipse.ditto.model.thingsearchparser.RqlOptionParser;
import org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException;
import org.eclipse.ditto.signals.commands.thingsearch.query.StreamThings;

import akka.actor.ActorRef;

/**
 * Builder for {@link org.eclipse.ditto.services.utils.search.SearchSource}.
 */
public final class SearchSourceBuilder {

    private static final String COMMA = ",";

    private static final SortOption DEFAULT_SORT_OPTION =
            SortOption.of(List.of(SortOptionEntry.asc(Thing.JsonFields.ID.getPointer())));

    private ActorRef pubSubMediator;
    private ActorRef conciergeForwarder;
    private JsonFieldSelector fields;
    private JsonFieldSelector sortFields;
    private String filter;
    private JsonArray namespaces;
    private String sort;
    private JsonArray sortValues;
    private DittoHeaders dittoHeaders;
    private Duration thingsAskTimeout = Duration.ofSeconds(10L);
    private Duration searchAskTimeout = Duration.ofSeconds(60L);
    private Duration minBackoff = Duration.ofSeconds(1L);
    private Duration maxBackoff = Duration.ofSeconds(30L);
    private int maxRetries = 5; // failure message after 32s on back-end error
    private Duration recovery = Duration.ofSeconds(90L);

    /**
     * Create a search-source from this builder.
     *
     * @return the search-source.
     * @throws java.lang.NullPointerException if required fields are not set.
     * @throws org.eclipse.ditto.signals.commands.thingsearch.exceptions.InvalidOptionException if the sort option is
     * invalid.
     * @throws org.eclipse.ditto.model.base.exceptions.InvalidRqlExpressionException if the filter expression is
     * invalid.
     */
    public SearchSource build() {
        final StreamThings streamThings = constructStreamThings();
        validate(streamThings);
        return new SearchSource(pubSubMediator, conciergeForwarder, thingsAskTimeout, searchAskTimeout, fields,
                sortFields, streamThings, minBackoff, maxBackoff, maxRetries, recovery);
    }

    /**
     * Set the pub-sub mediator for error reporting.
     *
     * @param pubSubMediator the pub-sub mediator.
     * @return this builder.
     */
    public SearchSourceBuilder pubSubMediator(final ActorRef pubSubMediator) {
        this.pubSubMediator = pubSubMediator;
        return this;
    }

    /**
     * Set the concierge forwarder to send commands.
     *
     * @param conciergeForwarder the concierge forwarder.
     * @return this builder.
     */
    public SearchSourceBuilder conciergeForwarder(final ActorRef conciergeForwarder) {
        this.conciergeForwarder = conciergeForwarder;
        return this;
    }

    /**
     * Set the selected fields of search results.
     *
     * @param fields the selected fields.
     * @return this builder.
     */
    public SearchSourceBuilder fields(@Nullable final JsonFieldSelector fields) {
        this.fields = fields;
        return this;
    }

    /**
     * Set the selected fields of search results from a comma-separated string.
     *
     * @param fieldsString selected fields as comma-separated string.
     * @return this builder.
     */
    public SearchSourceBuilder fields(@Nullable final String fieldsString) {
        if (fieldsString == null) {
            fields = null;
        } else {
            List<String> pointers = Arrays.stream(fieldsString.split(COMMA))
                    .filter(pointer -> !pointer.isBlank())
                    .collect(Collectors.toList());
            if (pointers.isEmpty()) {
                fields = null;
            } else {
                fields = JsonFieldSelector.newInstance(pointers.get(0),
                        pointers.stream().skip(1L).toArray(String[]::new));
            }
        }
        return this;
    }

    /**
     * Set the filter string of the search command.
     *
     * @param filter the filter.
     * @return this builder.
     */
    public SearchSourceBuilder filter(@Nullable final String filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Set the namespaces of the search command.
     *
     * @param namespaces the namespaces.
     * @return this builder.
     */
    public SearchSourceBuilder namespaces(@Nullable final JsonArray namespaces) {
        this.namespaces = namespaces;
        return this;
    }

    /**
     * Set the namespaces of the search command from a comma-separated string.
     *
     * @param namespacesString namespaces as a comma-separated string.
     * @return this builder.
     */
    public SearchSourceBuilder namespaces(@Nullable final String namespacesString) {
        if (namespacesString == null || namespacesString.isEmpty()) {
            namespaces = null;
        } else {
            namespaces = Arrays.stream(namespacesString.split(COMMA))
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());
        }
        return this;
    }

    /**
     * Set the sort option as a string with consideration to the default sort option.
     *
     * @param sort the sort string.
     * @return this builder.
     */
    public SearchSourceBuilder sort(@Nullable final String sort) {
        final SortOption sortOption = getEffectiveSortOption(sort);
        this.sort = sortOptionAsString(sortOption);
        sortFields = getSortFields(sortOption);
        return this;
    }

    /**
     * Set the sort option and sort fields from the value of the options parameter.
     *
     * @param option options as comma-separated string.
     * @return this builder.
     */
    public SearchSourceBuilder option(@Nullable final String option) {
        final SortOption sortOption = getEffectiveSortOption(option);
        sort = sortOptionAsString(sortOption);
        sortFields = getSortFields(sortOption);
        return this;
    }

    /**
     * Set the sort fields. Only used for tests. For other users, sort fields are set according to the sort option.
     *
     * @param sortFields the sort fields.
     * @return this builder.
     */
    SearchSourceBuilder sortFields(@Nullable final JsonFieldSelector sortFields) {
        this.sortFields = sortFields;
        return this;
    }

    /**
     * Set the sort values for cursor computation.
     *
     * @param sortValues values of the sort fields of the last result of a previous search.
     * @return this builder.
     */
    public SearchSourceBuilder sortValues(@Nullable final JsonArray sortValues) {
        this.sortValues = sortValues;
        return this;
    }

    /**
     * Set the Ditto headers to use including authorization context and correlation ID.
     *
     * @param dittoHeaders the Ditto headers to use.
     * @return this builder.
     */
    public SearchSourceBuilder dittoHeaders(final DittoHeaders dittoHeaders) {
        this.dittoHeaders = dittoHeaders;
        return this;
    }

    /**
     * Set the timeout when asking the things-shard-region.
     *
     * @param thingsAskTimeout the timeout.
     * @return this builder.
     */
    public SearchSourceBuilder thingsAskTimeout(final Duration thingsAskTimeout) {
        this.thingsAskTimeout = thingsAskTimeout;
        return this;
    }

    /**
     * Set the timeout when asking the search actor.
     *
     * @param searchAskTimeout the timeout.
     * @return this builder.
     */
    public SearchSourceBuilder searchAskTimeout(final Duration searchAskTimeout) {
        this.searchAskTimeout = searchAskTimeout;
        return this;
    }

    /**
     * Set the minimum backoff after failure.
     *
     * @param minBackoff the minimum backoff.
     * @return this builder.
     */
    public SearchSourceBuilder minBackoff(final Duration minBackoff) {
        this.minBackoff = minBackoff;
        return this;
    }

    /**
     * Set the maximum backoff after failure.
     *
     * @param maxBackoff the maximum backoff.
     * @return this builder.
     */
    public SearchSourceBuilder maxBackoff(final Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
        return this;
    }

    /**
     * Set the maximum number of retries after failure.
     *
     * @param maxRetries the maximum number of retries.
     * @return this builder.
     */
    public SearchSourceBuilder maxRetries(final int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * Set the time needed to run without errors before resetting backoff and retry counter.
     *
     * @param recovery the recovery period.
     * @return this builder.
     */
    public SearchSourceBuilder recovery(final Duration recovery) {
        this.recovery = recovery;
        return this;
    }

    private String sortOptionAsString(final SortOption sortOption) {
        return sortOption.getEntries()
                .stream()
                .map(SortOptionEntry::toString)
                .collect(Collectors.joining(COMMA, "sort(", ")"));
    }

    private JsonFieldSelector getSortFields(final SortOption sortOption) {
        return JsonFieldSelector.newInstance(
                sortOption.getEntries().get(0).getPropertyPath(),
                sortOption.getEntries()
                        .stream()
                        .skip(1L)
                        .map(SortOptionEntry::getPropertyPath)
                        .toArray(CharSequence[]::new)
        );
    }

    private SortOption getEffectiveSortOption(@Nullable final String optionString) {
        if (optionString == null || optionString.isEmpty()) {
            return DEFAULT_SORT_OPTION;
        } else {
            return appendIdField(findUniqueSortOption(optionString));
        }
    }

    private SortOption appendIdField(final SortOption parsedSortOption) {
        final boolean hasId = parsedSortOption.stream()
                .anyMatch(entry -> entry.getPropertyPath().equals(Thing.JsonFields.ID.getPointer()));
        if (hasId) {
            return parsedSortOption;
        } else {
            return SortOption.of(
                    Stream.concat(parsedSortOption.stream(), DEFAULT_SORT_OPTION.stream())
                            .collect(Collectors.toList())
            );
        }
    }

    private SortOption findUniqueSortOption(final String optionString) {
        final List<Option> parsedOptions;
        try {
            parsedOptions = RqlOptionParser.parseOptions(optionString);
        } catch (final ParserException e) {
            throw InvalidOptionException.newBuilder()
                    .message("Invalid options: " + optionString)
                    .build();
        }
        final List<SortOption> sortOptions = parsedOptions.stream()
                .flatMap(option -> option instanceof SortOption
                        ? Stream.of((SortOption) option)
                        : Stream.empty()
                )
                .collect(Collectors.toList());
        if (sortOptions.isEmpty()) {
            return DEFAULT_SORT_OPTION;
        } else if (sortOptions.size() == 1) {
            return sortOptions.get(0);
        } else {
            throw InvalidOptionException.newBuilder()
                    .message("Too many sort options.")
                    .description("0 or 1 sort option is expected.")
                    .build();
        }
    }

    private StreamThings constructStreamThings() {
        return StreamThings.of(filter, namespaces, sort, sortValues, dittoHeaders);
    }

    private void validate(final StreamThings streamThings) {
        try {
            streamThings.getFilter().ifPresent(new RqlPredicateParser()::parse);
        } catch (final ParserException e) {
            throw InvalidRqlExpressionException.newBuilder()
                    .message("Invalid filter expression: " + streamThings.getFilter().orElseThrow())
                    .build();
        }
        // check sort expressions
        try {
            streamThings.getSort().ifPresent(sort -> {
                final ThingsFieldExpressionFactory fieldExpressionFactory =
                        new ModelBasedThingsFieldExpressionFactory();
                for (final SortOptionEntry entry : getEffectiveSortOption(sort)) {
                    fieldExpressionFactory.sortBy(entry.getPropertyPath().toString());
                }
            });
        } catch (final IllegalArgumentException e) {
            throw InvalidOptionException.newBuilder()
                    .message(e.getMessage())
                    .description("The sort option is invalid.")
                    .build();
        }
    }
}
