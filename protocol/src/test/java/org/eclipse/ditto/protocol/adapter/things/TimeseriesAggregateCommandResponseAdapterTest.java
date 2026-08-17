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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseriesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Round-trip tests for {@link TimeseriesAggregateCommandResponseAdapter}.
 * <p>
 * The response entity carries {@code results} and {@code authorization} but <em>not</em> the
 * namespace — the HTTP caller knows it from the request URL. Over the Ditto protocol it has to be
 * recovered from the topic path, so these tests pin that specifically: a response that loses its
 * namespace, or drops the {@code authorization} block, would report a partial aggregate as if it
 * were complete.
 */
public final class TimeseriesAggregateCommandResponseAdapterTest {

    private static final String NAMESPACE = "org.eclipse.ditto";
    private static final JsonPointer FLOW =
            JsonPointer.of("/features/environment/properties/temperature");

    private TimeseriesAggregateCommandResponseAdapter underTest;

    @Before
    public void setUp() {
        underTest = TimeseriesAggregateCommandResponseAdapter.of(HeaderTranslator.empty());
    }

    @Test
    public void adapterReportsCorrectTopicPathSegments() {
        assertThat(underTest.getGroups()).containsExactly(TopicPath.Group.THINGS);
        assertThat(underTest.getChannels()).containsExactly(TopicPath.Channel.TWIN);
        assertThat(underTest.getCriteria()).containsExactly(TopicPath.Criterion.TIMESERIES);
        assertThat(underTest.getActions()).containsExactly(TopicPath.Action.AGGREGATE);
        assertThat(underTest.isForResponses()).isTrue();
    }

    @Test
    public void roundTripPreservesResultsAndNamespace() {
        final RetrieveAggregatedTimeseriesResponse original = sampleResponse();

        final Adaptable adaptable = underTest.toAdaptable(original, TopicPath.Channel.TWIN);
        final RetrieveAggregatedTimeseriesResponse reconstructed = underTest.fromAdaptable(adaptable);

        assertThat(reconstructed.getNamespace()).isEqualTo(NAMESPACE);
        assertThat(reconstructed.getResults()).isEqualTo(original.getResults());
    }

    /**
     * The honesty signal. If the authorization block did not survive the hop, a caller on WebSocket
     * would see an aggregate computed over a permitted subset and have no way to tell.
     */
    @Test
    public void roundTripPreservesTheAuthorizationSummary() {
        final RetrieveAggregatedTimeseriesResponse original = sampleResponse();

        final RetrieveAggregatedTimeseriesResponse reconstructed =
                underTest.fromAdaptable(underTest.toAdaptable(original, TopicPath.Channel.TWIN));

        assertThat(reconstructed.getContributingThings()).isEqualTo(4);
        assertThat(reconstructed.getExcludedThings()).isEqualTo(2);
        assertThat(reconstructed.getWithheldByPath())
                .containsEntry(FLOW.toString(), 2);
    }

    @Test
    public void topicPathCarriesTheNamespaceAndPlaceholderEntityName() {
        final Adaptable adaptable = underTest.toAdaptable(sampleResponse(), TopicPath.Channel.TWIN);

        assertThat(adaptable.getTopicPath().getPath())
                .isEqualTo(NAMESPACE + "/_/things/twin/timeseries/aggregate");
    }

    @Test
    public void dittoProtocolAdapterResolvesTheAggregateResponse() {
        final DittoProtocolAdapter protocolAdapter = DittoProtocolAdapter.newInstance();
        final Adaptable adaptable = underTest.toAdaptable(sampleResponse(), TopicPath.Channel.TWIN);

        final Signal<?> resolved = protocolAdapter.fromAdaptable(adaptable);

        assertThat(resolved).isInstanceOf(RetrieveAggregatedTimeseriesResponse.class);
    }

    private static RetrieveAggregatedTimeseriesResponse sampleResponse() {
        final Map<String, String> group = new LinkedHashMap<>();
        group.put("attributes/building", "A");
        final List<TimeseriesDataValue> data = Arrays.asList(
                TimeseriesDataValue.of(Instant.parse("2026-07-01T00:00:00Z"), JsonValue.of(23.5)),
                TimeseriesDataValue.of(Instant.parse("2026-07-01T01:00:00Z"), JsonValue.of(24.0)));
        final AggregatedTimeseriesResult result = AggregatedTimeseriesResult.of(group, FLOW,
                TimeseriesResultMeta.of(data.size(), null, "number"), data);

        final Map<String, Integer> withheld = new LinkedHashMap<>();
        withheld.put(FLOW.toString(), 2);

        return RetrieveAggregatedTimeseriesResponse.of(NAMESPACE,
                Collections.singletonList(result), 4, 2, withheld, DittoHeaders.empty());
    }
}
