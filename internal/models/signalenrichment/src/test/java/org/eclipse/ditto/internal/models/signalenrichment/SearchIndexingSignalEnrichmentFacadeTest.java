/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signalenrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.pekko.japi.Pair;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.common.LikeHelper;
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.DittoTestSystem;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link SearchIndexingSignalEnrichmentFacade}.
 */
public final class SearchIndexingSignalEnrichmentFacadeTest extends AbstractCachingSignalEnrichmentFacadeTest {


    private static final JsonObject EXPECTED_THING_JSON = JsonObject.of("""
            {
              "policyId": "policy:id",
              "attributes": {"x":  5},
              "_metadata": {"attributes": {"x": {"type": "x attribute"}}}
            }""");

    private static final JsonFieldSelector SELECTED_INDEXES =
            JsonFieldSelector.newInstance("policyId", "attributes/x", "_metadata");

    private static final JsonFieldSelector SELECTED_INDEXES_WILDCARD_NS =
            JsonFieldSelector.newInstance("attributes/wild");

    private static final AttributeModified THING_EVENT = AttributeModified.of(
            ThingId.generateRandom("org.eclipse.test"),
            JsonPointer.of("x"),
            JsonValue.of(5),
            3L,
            Instant.EPOCH,
            DittoHeaders.empty(),
            MetadataModelFactory.newMetadataBuilder()
                    .set("type", "x attribute")
                    .build());

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Override
    protected CachingSignalEnrichmentFacade createCachingSignalEnrichmentFacade(final TestKit kit,
            final ByRoundTripSignalEnrichmentFacade cacheLoaderFacade, final CacheConfig cacheConfig) {
        return SearchIndexingSignalEnrichmentFacade.newInstance(
                List.of(
                        Pair.create(
                                Pattern.compile(
                                        Objects.requireNonNull(LikeHelper.convertToRegexSyntax("org.eclipse.test"))),
                                SELECTED_INDEXES
                        ),
                        Pair.create(
                                Pattern.compile(
                                        Objects.requireNonNull(LikeHelper.convertToRegexSyntax("org.eclipse*"))),
                                SELECTED_INDEXES_WILDCARD_NS
                        )
                ),
                cacheLoaderFacade,
                cacheConfig,
                kit.getSystem().getDispatcher(),
                "test");
    }

    @Override
    protected JsonObject getExpectedThingJson() {
        return EXPECTED_THING_JSON;
    }

    @Override
    protected JsonFieldSelector getJsonFieldSelector() {
        return SELECTED_INDEXES;
    }

    @Override
    protected AttributeModified getThingEvent() {
        return THING_EVENT;
    }

    @Test
    public void determineRightSelectorForMultipleNamespacesConfigured() {
        DittoTestSystem.run(this, CONFIG,kit -> {
            final SearchIndexingSignalEnrichmentFacade underTest =
                    (SearchIndexingSignalEnrichmentFacade) createSignalEnrichmentFacadeUnderTest(kit, Duration.ofSeconds(10L));

            assertThat(underTest.determineSelector("org.eclipse.test"))
                    .isEqualTo(SELECTED_INDEXES);

            assertThat(underTest.determineSelector("org.eclipse"))
                    .isEqualTo(SELECTED_INDEXES_WILDCARD_NS);

            assertThat(underTest.determineSelector("org.eclipsefoo"))
                    .isEqualTo(SELECTED_INDEXES_WILDCARD_NS);

            assertThat(underTest.determineSelector("org.eclipse.wild"))
                    .isEqualTo(SELECTED_INDEXES_WILDCARD_NS);
        });
    }

}
