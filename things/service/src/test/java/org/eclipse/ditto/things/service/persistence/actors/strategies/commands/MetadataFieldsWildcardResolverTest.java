/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.things.model.signals.commands.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.signals.commands.TestConstants.Thing.THING;
import static org.eclipse.ditto.things.model.signals.commands.TestConstants.Thing.THING_ID;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link MetadataFieldsWildcardResolver}.
 */
public class MetadataFieldsWildcardResolverTest {

    private static final String GET_METADATA_HEADER_KEY = DittoHeaderDefinition.GET_METADATA.getKey();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(MetadataFieldsWildcardResolver.class, areImmutable());
    }

    @Test
    public void validateUnknownMetadataWildcardResolvesToEmptySet() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("features/*/bumlux/*/key");

        final Set<JsonPointer> expectedValues = Set.of();

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues)
                .containsExactlyElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnFeatureAndPropertyLevelForRetrieveThing() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("features/*/properties/*/key");

        final List<String> expectedEntries = List.of(
                "/features/FluxCapacitor/properties/target_year_1/key",
                "/features/FluxCapacitor/properties/target_year_2/key",
                "/features/FluxCapacitor/properties/target_year_3/key",
                "/features/HoverBoard/properties/height_above_ground/key",
                "/features/HoverBoard/properties/speed/key",
                "/features/HoverBoard/properties/stability_factor/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnPropertyLevelForRetrieveThing() {
        final JsonPointer metadataWildcardExpr =
                JsonPointer.of("features/FluxCapacitor/properties/*/key");

        final List<String> expectedEntries = List.of(
                "/features/FluxCapacitor/properties/target_year_1/key",
                "/features/FluxCapacitor/properties/target_year_2/key",
                "/features/FluxCapacitor/properties/target_year_3/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnFeatureLevelForRetrieveThing() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("features/*/properties/speed/key");

        final List<String> expectedEntries = List.of("/features/HoverBoard/properties/speed/key",
                "/features/FluxCapacitor/properties/speed/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnAttributesLevelForRetrieveThing() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("/attributes/*/description");

        final List<String> expectedEntries = List.of("/attributes/location/description",
                "/attributes/maker/description");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnLeafLevelForRetrieveThing() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("*/key");
        final Set<JsonPointer> expectedValues = Set.of(
                JsonPointer.of("/thingId/key"),
                JsonPointer.of("/policyId/key"),
                JsonPointer.of("/attributes/location/latitude/key"),
                JsonPointer.of("/attributes/location/longitude/key"),
                JsonPointer.of("/attributes/maker/key"),
                JsonPointer.of("/features/HoverBoard/properties/height_above_ground/key"),
                JsonPointer.of("/features/HoverBoard/properties/speed/key"),
                JsonPointer.of("/features/HoverBoard/desiredProperties/height_above_ground/key"),
                JsonPointer.of("/features/HoverBoard/properties/stability_factor/key"),
                JsonPointer.of("/features/HoverBoard/desiredProperties/stability_factor/key"),
                JsonPointer.of("/features/HoverBoard/desiredProperties/speed/key"),
                JsonPointer.of("/features/HoverBoard/definition/key"),
                JsonPointer.of("/features/FluxCapacitor/properties/target_year_1/key"),
                JsonPointer.of("/features/FluxCapacitor/properties/target_year_2/key"),
                JsonPointer.of("/features/FluxCapacitor/properties/target_year_3/key"),
                JsonPointer.of("/features/FluxCapacitor/definition/key"));


        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveThing.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr, GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnFeaturesAndPropertyLevelForRetrieveFeatures() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("/*/properties/*/key");

        final List<String> expectedEntries = List.of(
                "/FluxCapacitor/properties/target_year_1/key",
                "/FluxCapacitor/properties/target_year_2/key",
                "/FluxCapacitor/properties/target_year_3/key",
                "/HoverBoard/properties/height_above_ground/key",
                "/HoverBoard/properties/speed/key",
                "/HoverBoard/properties/stability_factor/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveFeatures.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnPropertyLevelForRetrieveFeatures() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("/FluxCapacitor/properties/*/key");

        final List<String> expectedEntries = List.of("/FluxCapacitor/properties/target_year_1/key",
                "/FluxCapacitor/properties/target_year_2/key",
                "/FluxCapacitor/properties/target_year_3/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveFeatures.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnFeaturesLevelForRetrieveFeatures() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("/*/properties/speed/key");

        final List<String> expectedEntries = List.of("/HoverBoard/properties/speed/key",
                "/FluxCapacitor/properties/speed/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveFeatures.of(THING_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnPropertyLevelForRetrieveFeature() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("/properties/*/key");

        final List<String> expectedEntries = List.of(
                "/properties/target_year_1/key",
                "/properties/target_year_2/key",
                "/properties/target_year_3/key");

        final Set<JsonPointer> expectedValues =
                expectedEntries.stream()
                        .map(JsonPointer::of)
                        .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                RetrieveFeature.of(THING_ID, FLUX_CAPACITOR_ID, DittoHeaders.empty()), THING, metadataWildcardExpr,
                GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedValues);
    }

    @Test
    public void validateMetadataWildcardResolvingOnThingLevelForMergeThing() {
        final JsonPointer metadataWildcardExpr = JsonPointer.of("features/*/definition/modified");

        final List<String> expectedEntries =
                List.of("/features/FluxCapacitor/definition/modified", "features/HoverBoard/definition/modified");
        final Set<JsonPointer> expectedElements = expectedEntries.stream()
                .map(JsonPointer::of)
                .collect(Collectors.toSet());

        final Set<JsonPointer> resolvedValues = MetadataFieldsWildcardResolver.resolve(
                MergeThing.withThing(THING_ID, THING, DittoHeaders.empty()),
                THING, metadataWildcardExpr, GET_METADATA_HEADER_KEY);

        softly.assertThat(resolvedValues).containsExactlyInAnyOrderElementsOf(expectedElements);
    }

}