/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSearchResult}
 */
public final class ImmutableSearchResultTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSearchResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSearchResult.class,
                areImmutable(),
                provided(JsonArray.class).areAlsoImmutable());
    }

    @Test
    public void constructorAndGetters() {
        final JsonArray items = JsonFactory.newArray("[1,2,3]");
        final long nextPageOffset = 10L;

        final SearchResult searchResult = ImmutableSearchResult.of(items, null);

        assertThat(searchResult.getItems()).isEqualTo(items);
    }

    @Test
    public void emptySearchResult() {
        final SearchResult searchResult = ImmutableSearchResult.empty();

        assertThat(searchResult.isEmpty()).isTrue();
        assertThat(searchResult.getSize()).isZero();
        assertThat(searchResult.getItems()).isEqualTo(JsonFactory.newArray());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonArray items = JsonFactory.newArrayBuilder()
                .add(1, 2, 3)
                .build();
        final long nextPageOffset = 10L;
        final JsonObject expected = JsonFactory.newObjectBuilder()
                .set(SearchResult.JsonFields.ITEMS, items)
                .build();

        final Jsonifiable<?> underTest = ImmutableSearchResult.of(items, null);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void toJsonWithAllFieldTypes() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, null);

        final String jsonStr = searchResult.toJsonString(FieldType.regularOrSpecial());

        assertThat(jsonStr).isEqualTo("{\"items\":" + itemsArray + "}");
    }

    @Test
    public void toJsonWithRegularFieldTypes() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final long nextPageOffset = 10L;
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, null);

        final String jsonStr = searchResult.toJsonString();

        assertThat(jsonStr).isEqualTo("{\"items\":" + itemsArray + "}");
    }

    @Test
    public void emptyToJsonWithAllFieldTypes() {
        final SearchResult searchResult = ImmutableSearchResult.empty();

        final String jsonStr = searchResult.toJsonString(FieldType.regularOrSpecial());

        assertThat(jsonStr).isEqualTo("{\"items\":[]}");
    }

    @Test
    public void fromJson() {
        final String itemsArray = "[1,2,3]";
        final String jsonStr = "{\"items\":" + itemsArray + "}";
        final JsonObject jsonObject = JsonFactory.newObject(jsonStr);

        final SearchResult searchResult = ImmutableSearchResult.fromJson(jsonObject);

        assertThat(searchResult.getItems()).isEqualTo(JsonFactory.newArray(itemsArray));
    }

    @Test
    public void fromEmptyJson() {
        final String jsonStr = "{\"items\":[]}";
        final JsonObject jsonObject = JsonFactory.newObject(jsonStr);

        final SearchResult searchResult = ImmutableSearchResult.fromJson(jsonObject);

        assertThat(searchResult).isEqualTo(ImmutableSearchResult.empty());
    }

    @Test
    public void ensureSearchResultNewBuilderWorks() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, null);

        final SearchResultBuilder searchResultBuilder = SearchResult.newBuilder()
                .addAll(items);

        assertThat(searchResult).isEqualTo(searchResultBuilder.build());
    }

    @Test
    public void ensureRelationsToBuilderWorks() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items,  null);

        DittoJsonAssertions.assertThat(searchResult).isEqualTo(searchResult.toBuilder().build());
    }

}
