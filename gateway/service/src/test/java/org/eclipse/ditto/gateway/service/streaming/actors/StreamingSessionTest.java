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

import java.time.Instant;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.placeholders.ResourcePlaceholder;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for the filter evaluation of {@link StreamingSession}.
 */
public final class StreamingSessionTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:thing");
    private static final ThingFieldSelector EXTRA_FIELDS = ThingFieldSelector.fromString("features/specificFeature");
    private static final JsonObject EXTRA = JsonObject.of(
            "{\"features\":{\"specificFeature\":{\"properties\":{\"unit\":\"Celsius\"}}}}");

    @Test
    public void filterSeesExtraFieldsButChangeFilterDoesNot() {
        final Criteria sameExpression = criteria("exists(features/specificFeature)");
        final StreamingSession underTest = session(sameExpression, sameExpression);
        final FeaturePropertyModified event = featurePropertyModified("otherFeature");

        assertThat(underTest.matchesChangeFilter(event)).isFalse();
        assertThat(underTest.matchesFilter(underTest.mergeThingWithExtra(event, EXTRA), event)).isTrue();
    }

    @Test
    public void changeFilterMatchesWhenTheChangeContainsThePath() {
        final StreamingSession underTest = session(criteria("exists(features/specificFeature)"), null);

        assertThat(underTest.matchesChangeFilter(featurePropertyModified("specificFeature"))).isTrue();
        assertThat(underTest.matchesChangeFilter(featurePropertyModified("otherFeature"))).isFalse();
    }

    @Test
    public void changeFilterMatchesThingMergedAtRootByItsPayload() {
        final StreamingSession underTest = session(criteria("exists(features/specificFeature)"), null);

        assertThat(underTest.matchesChangeFilter(thingMergedAtRoot("specificFeature"))).isTrue();
        assertThat(underTest.matchesChangeFilter(thingMergedAtRoot("otherFeature"))).isFalse();
    }

    @Test
    public void absentChangeFilterMatchesEverything() {
        assertThat(session(null, null).matchesChangeFilter(featurePropertyModified("otherFeature"))).isTrue();
    }

    private static StreamingSession session(@Nullable final Criteria changeFilter,
            @Nullable final Criteria filter) {
        return StreamingSession.of(List.of(), filter, changeFilter, EXTRA_FIELDS, ActorRef.noSender(),
                Mockito.mock(ThreadSafeDittoLoggingAdapter.class));
    }

    private static Criteria criteria(final String filter) {
        return QueryFilterCriteriaFactory.modelBased(RqlPredicateParser.getInstance(),
                        ResourcePlaceholder.getInstance())
                .filterCriteria(filter, DittoHeaders.empty());
    }

    private static FeaturePropertyModified featurePropertyModified(final String featureId) {
        return FeaturePropertyModified.of(THING_ID, featureId, JsonPointer.of("value"), JsonValue.of(42), 3L,
                Instant.now(), DittoHeaders.empty(), null);
    }

    private static ThingMerged thingMergedAtRoot(final String featureId) {
        final JsonObject patch = JsonObject.newBuilder()
                .set(JsonPointer.of("features/" + featureId + "/properties/value"), JsonValue.of(42))
                .build();
        return ThingMerged.of(THING_ID, JsonPointer.empty(), patch, 3L, Instant.now(), DittoHeaders.empty(), null);
    }

}
