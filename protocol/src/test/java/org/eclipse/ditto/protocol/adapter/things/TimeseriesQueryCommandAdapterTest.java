/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.things;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;
import org.junit.Before;
import org.junit.Test;

/**
 * Round-trip tests for {@link TimeseriesQueryCommandAdapter} — verifies a {@link RetrieveTimeseries}
 * survives signal → adaptable → signal without loss, and that the produced topic path matches the
 * concept-doc shape {@code <ns>/<name>/things/twin/timeseries/retrieve}.
 */
public final class TimeseriesQueryCommandAdapterTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");

    private TimeseriesQueryCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = TimeseriesQueryCommandAdapter.of(HeaderTranslator.empty());
    }

    @Test
    public void adapterReportsCorrectTopicPathSegments() {
        assertThat(underTest.getGroups()).containsExactly(TopicPath.Group.THINGS);
        assertThat(underTest.getChannels()).containsExactly(TopicPath.Channel.TWIN);
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.TIMESERIES);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.RETRIEVE);
        assertThat(underTest.isForResponses()).isFalse();
        assertThat(underTest.supportsWildcardTopics()).isFalse();
    }

    @Test
    public void toAdaptableProducesExpectedTopicPath() {
        final RetrieveTimeseries command = sampleCommand();

        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.TWIN);

        final TopicPath topicPath = adaptable.getTopicPath();
        assertThat(topicPath.getNamespace()).isEqualTo("org.eclipse.ditto");
        assertThat(topicPath.getEntityName()).isEqualTo("sensor-1");
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.TIMESERIES);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.RETRIEVE);
    }

    @Test
    public void roundTripPreservesQuery() {
        final RetrieveTimeseries original = sampleCommand();

        final Adaptable adaptable = underTest.toAdaptable(original, TopicPath.Channel.TWIN);
        final RetrieveTimeseries reconstructed = underTest.fromAdaptable(adaptable);

        assertThat(reconstructed.getQuery()).isEqualTo(original.getQuery());
        assertThat((Object) reconstructed.getEntityId()).isEqualTo(THING_ID);
    }

    @Test
    public void payloadValueContainsSerialisedQuery() {
        final RetrieveTimeseries command = sampleCommand();

        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.TWIN);

        assertThat(adaptable.getPayload().getValue()).isPresent();
        // Round-trip-equivalent check: the payload value parses back into the original query.
        final TimeseriesQuery parsed =
                TimeseriesQuery.fromJson(adaptable.getPayload().getValue().get().asObject());
        assertThat(parsed).isEqualTo(command.getQuery());
    }

    @Test
    public void getTypeReturnsRetrieveTimeseries() {
        final RetrieveTimeseries command = sampleCommand();
        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.TWIN);

        // Validate that the adapter can extract the right type from a parsed adaptable.
        assertThat(underTest.fromAdaptable(adaptable).getType()).isEqualTo(RetrieveTimeseries.TYPE);
    }

    private static RetrieveTimeseries sampleCommand() {
        final TimeseriesQuery query = TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"));
        return RetrieveTimeseries.of(query,
                DittoHeaders.newBuilder().correlationId("test-1").build());
    }
}
