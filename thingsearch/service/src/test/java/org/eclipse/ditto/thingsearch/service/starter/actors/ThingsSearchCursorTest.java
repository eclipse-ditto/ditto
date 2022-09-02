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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.InvalidOptionException;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.QueryThings;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.scala.MongoClient;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Sink;
import akka.testkit.javadsl.TestKit;
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
    public void cursorForCompositeSortValue() {
        final var config = ConfigFactory.load("actors-test");
        final ActorSystem actorSystem = ActorSystem.create("cursorForComplexSortOptions", config);
        try {
            final var json = JsonObject.of("{\n" +
                    "  \"S\": \"sort(+attributes/sortKey,+thingId)\",\n" +
                    "  \"V\": [{\"key\": \"value\"},\"x:1\"]\n" +
                    "}");

            final var underTest = ThingsSearchCursor.fromJson(json);

            final var command =
                    ThingsSearchCursor.adjust(Optional.of(underTest), QueryThings.of(DittoHeaders.empty()));
            final var searchConfig = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(config));
            final var parser = SearchRootActor.getQueryParser(searchConfig, actorSystem);
            final Query query = parser.parse(command).toCompletableFuture().join();
            final Query result = ThingsSearchCursor.adjust(Optional.of(underTest), query, parser.getCriteriaFactory());
            final var bson = CreateBsonVisitor.sudoApply(result.getCriteria())
                    .toBsonDocument(Document.class, MongoClient.DEFAULT_CODEC_REGISTRY());
            assertThat(bson.toJson().replaceAll("\\s", ""))
                    .contains("{\"t.attributes.sortKey\":{\"$gt\":{\"key\":\"value\"}}}");
        } finally {
            TestKit.shutdownActorSystem(actorSystem);
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
