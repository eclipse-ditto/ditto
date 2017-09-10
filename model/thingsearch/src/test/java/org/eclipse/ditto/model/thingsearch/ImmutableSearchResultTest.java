/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.thingsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
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

        final SearchResult searchResult = ImmutableSearchResult.of(items, nextPageOffset);

        assertThat(searchResult.getItems()).isEqualTo(items);
        assertThat(searchResult.getNextPageOffset()).isEqualTo(nextPageOffset);
        assertThat(searchResult.hasNextPage()).isTrue();
    }

    @Test
    public void emptySearchResult() {
        final SearchResult searchResult = ImmutableSearchResult.empty();

        assertThat(searchResult.isEmpty()).isTrue();
        assertThat(searchResult.getSize()).isZero();
        assertThat(searchResult.getItems()).isEqualTo(JsonFactory.newArray());
        assertThat(searchResult.getNextPageOffset()).isEqualTo(SearchResult.NO_NEXT_PAGE);
        assertThat(searchResult.hasNextPage()).isFalse();
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonArray items = JsonFactory.newArrayBuilder() //
                .add(1, 2, 3) //
                .build();
        final long nextPageOffset = 10L;
        final JsonObject expected = JsonFactory.newObjectBuilder() //
                .set(SearchResult.JsonFields.ITEMS, items) //
                .set(SearchResult.JsonFields.NEXT_PAGE_OFFSET, nextPageOffset) //
                .build();

        final Jsonifiable underTest = ImmutableSearchResult.of(items, nextPageOffset);

        assertThat(underTest.toJson()).isEqualTo(expected);
    }

    @Test
    public void toJsonWithAllFieldTypes() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final long nextPageOffset = 10L;
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, nextPageOffset);

        final String jsonStr = searchResult.toJsonString(FieldType.regularOrSpecial());

        assertThat(jsonStr).isEqualTo(
                "{\"__schemaVersion\":" + 2 + ",\"items\":" + itemsArray + ",\"nextPageOffset\":" + nextPageOffset +
                        "}");
    }

    @Test
    public void toJsonWithRegularFieldTypes() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final long nextPageOffset = 10L;
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, nextPageOffset);

        final String jsonStr = searchResult.toJsonString();

        assertThat(jsonStr).isEqualTo("{\"items\":" + itemsArray + ",\"nextPageOffset\":" + nextPageOffset + "}");
    }

    @Test
    public void emptyToJsonWithAllFieldTypes() {
        final SearchResult searchResult = ImmutableSearchResult.empty();

        final String jsonStr = searchResult.toJsonString(FieldType.regularOrSpecial());

        assertThat(jsonStr).isEqualTo("{\"__schemaVersion\":" + 2 + ",\"items\":[],\"nextPageOffset\":-1}");
    }

    @Test
    public void fromJson() {
        final String itemsArray = "[1,2,3]";
        final long nextPageOffset = 10L;
        final String jsonStr = "{\"items\":" + itemsArray + ",\"nextPageOffset\":" + nextPageOffset + "}";
        final JsonObject jsonObject = JsonFactory.newObject(jsonStr);

        final SearchResult searchResult = ImmutableSearchResult.fromJson(jsonObject);

        assertThat(searchResult.getItems()).isEqualTo(JsonFactory.newArray(itemsArray));
        assertThat(searchResult.getNextPageOffset()).isEqualTo(nextPageOffset);
    }

    @Test
    public void fromEmptyJson() {
        final String jsonStr = "{\"items\":[],\"nextPageOffset\":-1}";
        final JsonObject jsonObject = JsonFactory.newObject(jsonStr);

        final SearchResult searchResult = ImmutableSearchResult.fromJson(jsonObject);

        assertThat(searchResult).isEqualTo(ImmutableSearchResult.empty());
    }

    @Test
    public void ensureSearchResultNewBuilderWorks() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final long nextPageOffset = 10L;
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, nextPageOffset);

        final SearchResultBuilder searchResultBuilder = SearchResult.newBuilder()
                .addAll(items)
                .nextPageOffset(nextPageOffset);

        assertThat(searchResult).isEqualTo(searchResultBuilder.build());
    }

    @Test
    public void ensureRelationsToBuilderWorks() {
        final String itemsArray = "[1,2,3]";
        final JsonArray items = JsonFactory.newArray(itemsArray);
        final long nextPageOffset = 10L;
        final ImmutableSearchResult searchResult = ImmutableSearchResult.of(items, nextPageOffset);

        DittoJsonAssertions.assertThat(searchResult).isEqualTo(searchResult.toBuilder().build());
    }

}
