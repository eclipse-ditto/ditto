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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.signals.commands.thingsearch.query.QueryThings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.testkit.javadsl.TestKit;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.services.thingsearch.starter.actors.ThingsSearchCursor}.
 */
public final class ThingsSearchCursorTest {

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;

    @Before
    public void init() {
        actorSystem = ActorSystem.create();
        materializer = ActorMaterializer.create(actorSystem);
    }

    @After
    public void stop() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        materializer = null;
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingsSearchCursor.class).verify();
    }

    @Test
    public void encodeAndDecodeAreInverse() {
        final ThingsSearchCursor input = randomCursor();
        final ThingsSearchCursor decoded =
                ThingsSearchCursor.decode(input.encode(), materializer).toCompletableFuture().join();

        assertThat(decoded).isEqualTo(input);
    }

    @Test
    public void correlationIdsAreConcatenated() {
        // GIVEN: cursor and command have different correlation IDs
        final ThingsSearchCursor underTest = randomCursor();
        final String correlationIdFromCursor = getCorrelationId(underTest);

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

        // THEN: correlation ID of adjusted command contains those of both the cursor and the original command.
        assertThat(correlationIdOfAdjustedCommand).contains(correlationIdFromCursor);
        assertThat(correlationIdOfAdjustedCommand).contains(correlationIdFromCommand);
    }

    private static ThingsSearchCursor randomCursor() {
        return new ThingsSearchCursor("jsonFieldSelector-" + UUID.randomUUID().toString(),
                new HashSet<>(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString())),
                "correlation-id-" + UUID.randomUUID().toString(),
                Collections.singletonList(
                        SortOption.of(Collections.singletonList(ThingsSearchCursor.DEFAULT_SORT_OPTION_ENTRY))),
                "eq(attributes/x,\"" + UUID.randomUUID().toString() + "\")",
                JsonArray.of(JsonValue.of("thingId:" + UUID.randomUUID().toString())));
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
