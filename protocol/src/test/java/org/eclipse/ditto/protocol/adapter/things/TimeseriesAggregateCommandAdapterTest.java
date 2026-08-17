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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseries;
import org.junit.Before;
import org.junit.Test;

/**
 * Round-trip tests for {@link TimeseriesAggregateCommandAdapter} — verifies a
 * {@link RetrieveAggregatedTimeseries} survives signal → adaptable → signal without loss, and that
 * the topic path is {@code <namespace>/_/things/twin/timeseries/aggregate}.
 * <p>
 * The cross-Thing command carries no entity ID, so the entity-name position holds the {@code _}
 * placeholder while the namespace stays addressable. Only a round-trip test catches a mapping
 * strategy that silently drops part of the query.
 */
public final class TimeseriesAggregateCommandAdapterTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final JsonPointer FLOW =
            JsonPointer.of("/features/environment/properties/temperature");
    private static final JsonPointer RETURN =
            JsonPointer.of("/features/environment/properties/returnTemperature");

    private TimeseriesAggregateCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = TimeseriesAggregateCommandAdapter.of(HeaderTranslator.empty());
    }

    @Test
    public void adapterReportsCorrectTopicPathSegments() {
        assertThat(underTest.getGroups()).containsExactly(TopicPath.Group.THINGS);
        assertThat(underTest.getChannels()).containsExactly(TopicPath.Channel.TWIN);
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.TIMESERIES);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.AGGREGATE);
        assertThat(underTest.isForResponses()).isFalse();
        assertThat(underTest.supportsWildcardTopics()).isFalse();
    }

    @Test
    public void toAdaptableProducesNamespaceScopedTopicPathWithPlaceholderEntityName() {
        final Adaptable adaptable = underTest.toAdaptable(sampleCommand(), TopicPath.Channel.TWIN);

        final TopicPath topicPath = adaptable.getTopicPath();
        assertThat(topicPath.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(topicPath.getEntityName()).isEqualTo("_");
        assertThat(topicPath.getGroup()).isEqualTo(TopicPath.Group.THINGS);
        assertThat(topicPath.getChannel()).isEqualTo(TopicPath.Channel.TWIN);
        assertThat(topicPath.getCriterion()).isEqualTo(TopicPath.Criterion.TIMESERIES);
        assertThat(topicPath.getAction()).contains(TopicPath.Action.AGGREGATE);
        assertThat(topicPath.getPath())
                .isEqualTo(NAMESPACE + "/_/things/twin/timeseries/aggregate");
    }

    /** Every optional field populated, so a strategy that drops one is caught. */
    @Test
    public void roundTripPreservesTheWholeQuery() {
        final RetrieveAggregatedTimeseries original = sampleCommand();

        final Adaptable adaptable = underTest.toAdaptable(original, TopicPath.Channel.TWIN);
        final RetrieveAggregatedTimeseries reconstructed = underTest.fromAdaptable(adaptable);

        assertThat(reconstructed.getQuery()).isEqualTo(original.getQuery());
        assertThat(reconstructed.getNamespace()).isEqualTo(NAMESPACE);
    }

    @Test
    public void roundTripPreservesGroupByAndFilter() {
        final RetrieveAggregatedTimeseries original = sampleCommand();

        final CrossThingTimeseriesQuery query =
                underTest.fromAdaptable(underTest.toAdaptable(original, TopicPath.Channel.TWIN))
                        .getQuery();

        assertThat(query.getGroupBy())
                .containsExactly(GroupBy.tag("attributes/building"), GroupBy.thingId());
        assertThat(query.getFilter()).contains("eq(attributes/building,'A')");
        assertThat(query.getPaths()).containsExactly(FLOW, RETURN);
        assertThat(query.getTimezone()).contains(ZoneId.of("Europe/Berlin"));
        assertThat(query.getFillStrategy()).contains(FillStrategy.LINEAR);
        assertThat(query.getMaxGroups()).contains(42);
    }

    @Test
    public void payloadValueParsesBackIntoTheQuery() {
        final RetrieveAggregatedTimeseries command = sampleCommand();

        final Adaptable adaptable = underTest.toAdaptable(command, TopicPath.Channel.TWIN);

        assertThat(adaptable.getPayload().getValue()).isPresent();
        final CrossThingTimeseriesQuery parsed = CrossThingTimeseriesQuery.fromJson(
                adaptable.getPayload().getValue().orElseThrow().asObject());
        assertThat(parsed).isEqualTo(command.getQuery());
    }

    /** Headers must survive the hop, or correlation-id based request/response matching breaks. */
    @Test
    public void roundTripPreservesCorrelationId() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .correlationId("ts-aggregate-1")
                .build();
        final RetrieveAggregatedTimeseries original =
                RetrieveAggregatedTimeseries.of(sampleQuery(), headers);

        final RetrieveAggregatedTimeseries reconstructed =
                underTest.fromAdaptable(underTest.toAdaptable(original, TopicPath.Channel.TWIN));

        assertThat(reconstructed.getDittoHeaders().getCorrelationId()).contains("ts-aggregate-1");
    }

    /**
     * The end-to-end dispatch, not just this adapter in isolation. Before the aggregate adapter was
     * registered, an inbound {@code .../timeseries/aggregate} topic failed resolution with
     * {@code protocoladapter:unknown.topicpath — Action name <aggregate> is unknown}; that is what a
     * WebSocket or Connectivity client saw. Resolving through {@link DittoProtocolAdapter} is the
     * only assertion that covers the resolver wiring rather than the adapter alone.
     */
    @Test
    public void dittoProtocolAdapterResolvesTheAggregateTopicPath() {
        final DittoProtocolAdapter protocolAdapter = DittoProtocolAdapter.newInstance();
        final Adaptable adaptable =
                underTest.toAdaptable(sampleCommand(), TopicPath.Channel.TWIN);

        final Signal<?> resolved = protocolAdapter.fromAdaptable(adaptable);

        assertThat(resolved).isInstanceOf(RetrieveAggregatedTimeseries.class);
        assertThat(((RetrieveAggregatedTimeseries) resolved).getQuery())
                .isEqualTo(sampleQuery());
    }

    private static RetrieveAggregatedTimeseries sampleCommand() {
        return RetrieveAggregatedTimeseries.of(sampleQuery(), DittoHeaders.empty());
    }

    private static CrossThingTimeseriesQuery sampleQuery() {
        final List<JsonPointer> paths = Arrays.asList(FLOW, RETURN);
        final List<GroupBy> groupBy =
                Arrays.asList(GroupBy.tag("attributes/building"), GroupBy.thingId());
        return CrossThingTimeseriesQuery.of(NAMESPACE, paths,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                Duration.ofHours(1), Aggregation.AVG,
                groupBy, "eq(attributes/building,'A')",
                ZoneId.of("Europe/Berlin"), FillStrategy.LINEAR, 42);
    }
}
