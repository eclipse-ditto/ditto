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
package org.eclipse.ditto.internal.utils.search;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

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
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.things.ModelBasedThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.thingsearch.model.Option;
import org.eclipse.ditto.thingsearch.model.SizeOption;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
import org.eclipse.ditto.rql.parser.thingsearch.RqlOptionParser;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.StreamThings;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;

/**
 * Builder for {@link SearchSource}.
 */
public final class SearchSourceBuilder {

    private static final String COMMA = ",";

    private static final SortOption DEFAULT_SORT_OPTION =
            SortOption.of(List.of(SortOptionEntry.asc(Thing.JsonFields.ID.getPointer())));

    private ActorRef pubSubMediator;
    private ActorSelection conciergeForwarder;
    private JsonFieldSelector fields;
    private JsonFieldSelector sortFields;
    private String filter;
    private JsonArray namespaces;
    private String sort;
    private JsonArray sortValues;
    private DittoHeaders dittoHeaders;
    private Duration thingsAskTimeout = Duration.ofSeconds(10L);
    private Duration searchAskTimeout = Duration.ofSeconds(60L);
    private String lastThingId = "";

    /**
     * Create a search-source from this builder.
     *
     * @return the search-source.
     * @throws java.lang.NullPointerException if required fields are not set.
     * @throws org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException if the sort option is
     * invalid.
     * @throws org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException if the filter expression is
     * invalid.
     */
    public SearchSource build() {
        final StreamThings streamThings = constructStreamThings();
        return new SearchSource(
                checkNotNull(pubSubMediator, "pubSubMediator"),
                checkNotNull(conciergeForwarder, "conciergeForwarder"),
                thingsAskTimeout,
                searchAskTimeout,
                fields,
                sortFields,
                streamThings,
                lastThingId);
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
    public SearchSourceBuilder conciergeForwarder(final ActorSelection conciergeForwarder) {
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
        this.filter = validateFilter(filter);
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
        return validateAndSetSortOption(getEffectiveSortOption(sort));
    }

    /**
     * Set the sort option and sort fields from the value of the options parameter.
     *
     * @param options options as comma-separated string.
     * @return this builder.
     */
    public SearchSourceBuilder options(@Nullable final String options) {
        return validateAndSetSortOption(getEffectiveSortOption(options));
    }

    private SearchSourceBuilder validateAndSetSortOption(final SortOption sortOption) {
        sort = sortOptionAsString(validateSortOption(sortOption));
        sortFields = getSortFields(sortOption);
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
     * Set the last thing ID to resume from.
     *
     * @param lastThingId the last thing ID.
     * @return this builder.
     */
    public SearchSourceBuilder lastThingId(final String lastThingId) {
        this.lastThingId = checkNotNull(lastThingId, "lastThingId");
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
        checkForUnsupportedOptions(parsedOptions);
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
        return StreamThings.of(filter, namespaces, sort, sortValues, checkNotNull(dittoHeaders, "dittoHeaders"));
    }

    @Nullable
    private String validateFilter(@Nullable final String filter) {
        if (filter != null) {
            try {
                RqlPredicateParser.getInstance().parse(filter);
            } catch (final ParserException e) {
                throw InvalidRqlExpressionException.newBuilder()
                        .message("Invalid filter expression: " + filter)
                        .build();
            }
        }
        return filter;
    }

    private SortOption validateSortOption(final SortOption sort) {
        // check sort expressions
        try {
            final ThingsFieldExpressionFactory fieldExpressionFactory =
                    ModelBasedThingsFieldExpressionFactory.getInstance();
            for (final SortOptionEntry entry : sort) {
                fieldExpressionFactory.sortBy(entry.getPropertyPath().toString());
            }
        } catch (final IllegalArgumentException e) {
            throw InvalidOptionException.newBuilder()
                    .message(e.getMessage())
                    .description("The sort option is invalid.")
                    .build();
        }
        return sort;
    }

    private static void checkForUnsupportedOptions(final List<Option> options) {
        for (final Option option : options) {
            if (!(option instanceof SortOption || option instanceof SizeOption)) {
                throw InvalidOptionException.newBuilder()
                        .message("The option " + option + " is not supported at this endpoint.")
                        .build();
            }
        }
    }
}
