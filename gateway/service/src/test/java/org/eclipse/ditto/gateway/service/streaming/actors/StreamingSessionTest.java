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
package org.eclipse.ditto.gateway.service.streaming.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.apache.pekko.actor.ActorRef;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link StreamingSession}.
 */
public final class StreamingSessionTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:my-thing");

    @Test
    public void withoutFilterEverySignalMatches() {
        final StreamingSession underTest = sessionWithFilter(null);

        assertThat(matches(underTest, thingDeleted(DittoHeaders.empty()))).isTrue();
    }

    @Test
    public void resolvesThingPlaceholder() {
        final Signal<?> signal = thingDeleted(DittoHeaders.empty());

        assertThat(matches(sessionWithFilter("eq(thing:name,'my-thing')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(thing:namespace,'org.eclipse.ditto')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(thing:id,'org.eclipse.ditto:my-thing')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(thing:name,'other-thing')"), signal)).isFalse();
    }

    @Test
    public void resolvesEntityPlaceholder() {
        final Signal<?> signal = thingDeleted(DittoHeaders.empty());

        assertThat(matches(sessionWithFilter("eq(entity:id,'org.eclipse.ditto:my-thing')"), signal)).isTrue();
    }

    @Test
    public void resolvesFeaturePlaceholderOfFeatureEvent() {
        final Signal<?> signal = FeaturePropertiesModified.of(THING_ID, "lamp",
                FeatureProperties.newBuilder().set("on", true).build(), 1L, null, DittoHeaders.empty(), null);

        assertThat(matches(sessionWithFilter("eq(feature:id,'lamp')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("exists(feature:id)"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(feature:id,'other')"), signal)).isFalse();
    }

    @Test
    public void resolvesAllFeatureIdsOfMergeOnFeatures() {
        final Features features = Features.newBuilder()
                .set(Feature.newBuilder().withId("lamp").build())
                .set(Feature.newBuilder().withId("temperature").build())
                .build();
        final Signal<?> signal = ThingMerged.of(THING_ID, JsonPointer.of("/features"), features.toJson(), 1L, null,
                DittoHeaders.empty(), null);

        assertThat(matches(sessionWithFilter("eq(feature:id,'lamp')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(feature:id,'temperature')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("ne(feature:id,'temperature')"), signal)).isFalse();
        assertThat(matches(sessionWithFilter("ne(feature:id,'other')"), signal)).isTrue();
    }

    @Test
    public void featurePlaceholderIsAbsentForSignalWithoutFeature() {
        final Signal<?> signal = thingDeleted(DittoHeaders.empty());

        assertThat(matches(sessionWithFilter("exists(feature:id)"), signal)).isFalse();
    }

    @Test
    public void resolvesTopicAndResourcePlaceholders() {
        final Signal<?> signal = thingDeleted(DittoHeaders.empty());

        assertThat(matches(sessionWithFilter("eq(topic:action,'deleted')"), signal)).isTrue();
        assertThat(matches(sessionWithFilter("eq(resource:path,'/')"), signal)).isTrue();
    }

    @Test
    public void rejectsPlaceholdersWhichAreNotResolved() {
        assertThatExceptionOfType(InvalidRqlExpressionException.class)
                .isThrownBy(() -> StreamingSession.getFilterCriteriaFactory()
                        .filterCriteria("eq(policy:id,'org.eclipse.ditto:policy')", DittoHeaders.empty()));
        assertThatExceptionOfType(InvalidRqlExpressionException.class)
                .isThrownBy(() -> StreamingSession.getFilterCriteriaFactory()
                        .filterCriteria("eq(header:my-header,'my-value')", DittoHeaders.empty()));
        assertThatExceptionOfType(InvalidRqlExpressionException.class)
                .isThrownBy(() -> StreamingSession.getFilterCriteriaFactory()
                        .filterCriteria("eq(thing:unknown,'my-thing')", DittoHeaders.empty()));
    }

    private static StreamingSession sessionWithFilter(final String filter) {
        return StreamingSession.of(List.of(),
                filter == null ? null :
                        StreamingSession.getFilterCriteriaFactory().filterCriteria(filter, DittoHeaders.empty()),
                null,
                ActorRef.noSender(),
                Mockito.mock(ThreadSafeDittoLoggingAdapter.class));
    }

    private static boolean matches(final StreamingSession session, final Signal<?> signal) {
        return session.matchesFilter(session.mergeThingWithExtra(signal, JsonObject.empty()), signal);
    }

    private static Signal<?> thingDeleted(final DittoHeaders dittoHeaders) {
        return ThingDeleted.of(THING_ID, 1L, null, dittoHeaders, null);
    }

}
