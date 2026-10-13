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
package org.eclipse.ditto.timeseries.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableWotTimeseriesAnnotation}.
 */
public final class ImmutableWotTimeseriesAnnotationTest {

    private static Map<String, String> sampleTags() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("attributes/building", "{{ thing-json:attributes/building }}");
        map.put("environment", "production");
        return map;
    }

    @Test
    public void hashCodeAndEqualsContract() {
        EqualsVerifier.forClass(ImmutableWotTimeseriesAnnotation.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void factoryCreatesInstanceWithIngestAndTags() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.ALL, sampleTags());

        assertThat((Object) underTest.getIngest()).isEqualTo(Ingest.ALL);
        assertThat(underTest.isIngestEnabled()).isTrue();
        final Map<String, String> tags = underTest.getTags();
        assertThat(tags).containsAllEntriesOf(sampleTags());
    }

    @Test
    public void factoryAcceptsEmptyTags() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.NONE, Collections.emptyMap());

        assertThat(underTest.getTags()).isEmpty();
        assertThat(underTest.isIngestEnabled()).isFalse();
    }

    @Test
    public void factoryRejectsNullIngest() {
        assertThatNullPointerException().isThrownBy(() ->
                WotTimeseriesAnnotation.of(null, Collections.emptyMap()));
    }

    @Test
    public void factoryRejectsNullTags() {
        assertThatNullPointerException().isThrownBy(() ->
                WotTimeseriesAnnotation.of(Ingest.ALL, null));
    }

    @Test
    public void getTagsReturnsAnUnmodifiableMap() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.ALL, sampleTags());

        final Map<String, String> tags = underTest.getTags();
        assertThat(tags).isUnmodifiable();
    }

    @Test
    public void factoryDefensivelyCopiesTags() {
        final Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("attributes/building", "A");
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.ALL, mutable);

        mutable.put("attributes/floor", "9");

        assertThat(underTest.getTags()).containsOnlyKeys("attributes/building");
    }

    // --- toJson / fromJson round-trip ---

    @Test
    public void toJsonContainsIngestAndTags() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.ALL, sampleTags());

        final JsonObject json = underTest.toJson();

        assertThat(json.getValue("ingest")).contains(JsonValue.of("ALL"));
        assertThat(json.getValue("tags")).isPresent();
    }

    @Test
    public void toJsonOmitsTagsWhenEmpty() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.NONE, Collections.emptyMap());

        final JsonObject json = underTest.toJson();

        assertThat(json.contains("tags")).isFalse();
    }

    @Test
    public void roundTripPreservesAllFields() {
        final WotTimeseriesAnnotation original =
                WotTimeseriesAnnotation.of(Ingest.ALL, sampleTags());

        final WotTimeseriesAnnotation reconstructed =
                WotTimeseriesAnnotation.fromJson(original.toJson());

        assertThat(reconstructed).isEqualTo(original);
    }

    @Test
    public void fromJsonRejectsMissingIngest() {
        final JsonObject json = JsonFactory.newObjectBuilder().build();

        assertThatExceptionOfType(JsonMissingFieldException.class)
                .isThrownBy(() -> WotTimeseriesAnnotation.fromJson(json));
    }

    @Test
    public void fromJsonRejectsUnknownIngestValue() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("ingest", "OPPORTUNISTIC")
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> WotTimeseriesAnnotation.fromJson(json))
                .withMessageContaining("OPPORTUNISTIC");
    }

    @Test
    public void fromJsonRejectsNonStringTagValue() {
        final JsonObject tagsJson = JsonFactory.newObjectBuilder().set("attributes/floor", 2).build();
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("ingest", "ALL")
                .set("tags", tagsJson)
                .build();

        assertThatExceptionOfType(DittoJsonException.class)
                .isThrownBy(() -> WotTimeseriesAnnotation.fromJson(json));
    }

    @Test
    public void fromJsonRejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> WotTimeseriesAnnotation.fromJson(null));
    }

    @Test
    public void fromJsonAcceptsAnnotationWithoutTags() {
        final JsonObject json = JsonFactory.newObjectBuilder().set("ingest", "ALL").build();

        final WotTimeseriesAnnotation underTest = WotTimeseriesAnnotation.fromJson(json);

        assertThat(underTest.getTags()).isEmpty();
    }

    // --- findInProperty ---

    @Test
    public void findInPropertyExtractsAnnotationFromPropertySchema() {
        final JsonObject propertySchema = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .set("unit", "cel")
                .set(WotTimeseriesAnnotation.EXTENSION_KEY, JsonFactory.newObjectBuilder()
                        .set("ingest", "ALL")
                        .build())
                .build();

        final Optional<WotTimeseriesAnnotation> found =
                WotTimeseriesAnnotation.findInProperty(propertySchema);

        assertThat(found).isPresent();
        assertThat((Object) found.get().getIngest()).isEqualTo(Ingest.ALL);
    }

    @Test
    public void findInPropertyReturnsEmptyWhenAnnotationAbsent() {
        final JsonObject propertySchema = JsonFactory.newObjectBuilder()
                .set("type", "number")
                .build();

        final Optional<WotTimeseriesAnnotation> found =
                WotTimeseriesAnnotation.findInProperty(propertySchema);

        assertThat(found).isEmpty();
    }

    @Test
    public void findInPropertyIgnoresNonObjectAnnotationValue() {
        // A malformed model where the extension key is set to a string. We treat this as
        // "no annotation" rather than throwing, since the ThingModel-validator catches that case
        // separately.
        final JsonObject propertySchema = JsonFactory.newObjectBuilder()
                .set(WotTimeseriesAnnotation.EXTENSION_KEY, "not-an-object")
                .build();

        final Optional<WotTimeseriesAnnotation> found =
                WotTimeseriesAnnotation.findInProperty(propertySchema);

        assertThat(found).isEmpty();
    }

    @Test
    public void findInPropertyRejectsNullInput() {
        assertThatNullPointerException()
                .isThrownBy(() -> WotTimeseriesAnnotation.findInProperty(null));
    }

    @Test
    public void toStringIncludesAllFields() {
        final WotTimeseriesAnnotation underTest =
                WotTimeseriesAnnotation.of(Ingest.ALL, sampleTags());

        final String s = underTest.toString();

        assertThat(s).contains("ALL").contains("attributes/building");
    }
}
