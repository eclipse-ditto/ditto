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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ThingsSearchCursor}.
 */
public final class ThingsSearchCursorTest {

    private ActorSystem actorSystem;

    @Before
    public void init() {
        actorSystem = ActorSystem.create();
    }

    @After
    public void stop() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingsSearchCursor.class).verify();
    }

    @Test
    public void encodeAndDecodeAreInverse() {
        final ThingsSearchCursor input = randomCursor();
        final ThingsSearchCursor decoded =
                ThingsSearchCursor.decode(input.encode(), actorSystem)
                        .runWith(Sink.head(), actorSystem)
                        .toCompletableFuture().join();

        assertThat(decoded).isEqualTo(input);
    }

    @Test
    public void correlationIdIsUnchanged() {
        // GIVEN: cursor and command have different correlation IDs
        final ThingsSearchCursor underTest = randomCursor();

        final String correlationIdFromCommand = "queryThingsCorrelationId";
        final QueryThings queryThings =
                withCursor(QueryThings.of(DittoHeaders.newBuilder().correlationId(correlationIdFromCommand).build()),
                        underTest);

        // WHEN: command is adjusted by cursor
        final String correlationIdOfAdjustedCommand =
                ThingsSearchCursor.adjust(Optional.of(underTest), queryThings)
                        .getDittoHeaders()
                        .getCorrelationId()
                        .orElse(null);

        // THEN: correlation ID of adjusted command is identical to the command's correlation ID
        assertThat(correlationIdOfAdjustedCommand)
                .isEqualTo(correlationIdFromCommand)
                .isNotEqualTo(getCorrelationId(underTest));
    }

    @Test
    public void decodingInvalidCursorsFailsWithInvalidCursorException() {
        assertThat(
                ThingsSearchCursor.decode("null", actorSystem)
                        .<Object>map(x -> x)
                        .recover(new PFBuilder<Throwable, Object>().matchAny(x -> x).build())
                        .runWith(Sink.head(), actorSystem)
                        .toCompletableFuture()
                        .join())
                .isInstanceOf(InvalidOptionException.class);
    }

    @Test
    public void objectSortValueResumesThroughProvider() {
        final JsonObject objectValue = JsonObject.of("{\"key\":\"value\"}");
        final SearchPersistenceProvider provider = mock(SearchPersistenceProvider.class);
        when(provider.compositeSortResumeValue(any())).thenReturn(SENTINEL);

        final Adjusted adjusted = adjustWithProvider(JsonArray.of(objectValue, JsonValue.of("x:1")), provider);

        assertThat(adjusted.result.getCriteria()).isEqualTo(adjusted.expectedWithValue(SENTINEL));
        verify(provider, times(2)).compositeSortResumeValue(objectValue);
        verifyNoMoreInteractions(provider);
    }

    @Test
    public void arraySortValueResumesThroughProvider() {
        final JsonArray arrayValue = JsonArray.of(1, 2);
        final SearchPersistenceProvider provider = mock(SearchPersistenceProvider.class);
        when(provider.compositeSortResumeValue(any())).thenReturn(SENTINEL);

        final Adjusted adjusted = adjustWithProvider(JsonArray.of(arrayValue, JsonValue.of("x:1")), provider);

        assertThat(adjusted.result.getCriteria()).isEqualTo(adjusted.expectedWithValue(SENTINEL));
        verify(provider, times(2)).compositeSortResumeValue(arrayValue);
        verifyNoMoreInteractions(provider);
    }

    @Test
    public void scalarAndNullSortValuesNeverCallProvider() {
        final SearchPersistenceProvider provider = mock(SearchPersistenceProvider.class);
        // a wrongly routed call would succeed with the sentinel; only the verify below catches it
        when(provider.compositeSortResumeValue(any())).thenReturn(SENTINEL);

        final Adjusted string = adjustWithProvider(JsonArray.of(JsonValue.of("abc"), JsonValue.of("x:1")), provider);
        assertThat(string.result.getCriteria()).isEqualTo(string.expectedWithValue("abc"));

        final Adjusted number = adjustWithProvider(JsonArray.of(JsonValue.of(42), JsonValue.of("x:1")), provider);
        assertThat(number.result.getCriteria()).isEqualTo(number.expectedWithValue(42));

        final Adjusted nullValue =
                adjustWithProvider(JsonArray.of(JsonValue.nullLiteral(), JsonValue.of("x:1")), provider);
        assertThat(nullValue.result.getCriteria()).isEqualTo(nullValue.expectedWithNull());

        verify(provider, never()).compositeSortResumeValue(any());
    }

    private static final Object SENTINEL = new Object();

    private Adjusted adjustWithProvider(final JsonArray values, final SearchPersistenceProvider provider) {
        final var config = ConfigFactory.load("actors-test");
        final ActorSystem system = ActorSystem.create("cursorResumeThroughProvider", config);
        try {
            final var json = JsonObject.newBuilder()
                    .set("S", "sort(+attributes/sortKey,+thingId)")
                    .set("V", values)
                    .build();
            final var underTest = ThingsSearchCursor.fromJson(json);
            final var command =
                    ThingsSearchCursor.adjust(Optional.of(underTest), QueryThings.of(DittoHeaders.empty()));
            final var searchConfig = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(config));
            final var parser = SearchRootActor.getQueryParser(searchConfig, system);
            final Query query = parser.parse(command).toCompletableFuture().join();
            final CriteriaFactory cf = parser.getCriteriaFactory();
            final Query result = ThingsSearchCursor.adjust(Optional.of(underTest), query, cf, provider);
            return new Adjusted(query, result, cf);
        } finally {
            TestKit.shutdownActorSystem(system);
        }
    }

    private static final class Adjusted {

        private final Query query;
        private final Query result;
        private final CriteriaFactory cf;

        private Adjusted(final Query query, final Query result, final CriteriaFactory cf) {
            this.query = query;
            this.result = result;
            this.cf = cf;
        }

        private SortFieldExpression sortKey() {
            return query.getSortOptions().get(0).getSortExpression();
        }

        private Criteria thingIdGt() {
            return cf.fieldCriteria(query.getSortOptions().get(1).getSortExpression(), cf.gt("x:1"));
        }

        private Criteria expectedWithValue(final Object value) {
            return cf.and(Arrays.asList(query.getCriteria(), cf.or(Arrays.asList(
                    cf.fieldCriteria(sortKey(), cf.gt(value)),
                    cf.and(Arrays.asList(cf.fieldCriteria(sortKey(), cf.eq(value)), thingIdGt()))))));
        }

        private Criteria expectedWithNull() {
            final Criteria eqNull = cf.or(Arrays.asList(
                    cf.nor(cf.existsCriteria(sortKey())),
                    cf.fieldCriteria(sortKey(), cf.eq(null))));
            return cf.and(Arrays.asList(query.getCriteria(), cf.or(Arrays.asList(
                    cf.existsCriteria(sortKey()),
                    cf.and(Arrays.asList(eqNull, thingIdGt()))))));
        }

    }

    private static ThingsSearchCursor randomCursor() {
        return new ThingsSearchCursor(
                new HashSet<>(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString())),
                "correlation-id-" + UUID.randomUUID(),
                SortOption.of(Collections.singletonList(ThingsSearchCursor.DEFAULT_SORT_OPTION_ENTRY)),
                "eq(attributes/x,\"" + UUID.randomUUID() + "\")",
                JsonArray.of(JsonValue.of("thingId:" + UUID.randomUUID())));
    }

    private static QueryThings withCursor(final QueryThings queryThings, final ThingsSearchCursor cursor) {
        final List<String> options = Collections.singletonList("cursor(" + cursor.encode() + ")");
        return QueryThings.of(queryThings.getFilter().orElse(null), options, queryThings.getFields().orElse(null),
                queryThings.getNamespaces().orElse(null), queryThings.getDittoHeaders());
    }

    @Nullable
    private static String getCorrelationId(final ThingsSearchCursor cursor) {
        return cursor.correlationId;
    }

}
