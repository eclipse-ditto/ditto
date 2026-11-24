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
package org.eclipse.ditto.thingsearch.persistence.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Tests {@link SearchIndexDocument}.
 */
public final class SearchIndexDocumentTest {

    private static final ThingId THING_ID = ThingId.of("thing:encoded");

    @Test
    public void encodingIsComputedOnceAndReturnedAgain() {
        final SearchIndexDocument document = document();
        final AtomicInteger calls = new AtomicInteger();
        final Function<SearchIndexDocument, Object> encoder = d -> {
            calls.incrementAndGet();
            return new Object();
        };

        final Object first = document.encoded(Object.class, encoder);
        final Object second = document.encoded(Object.class, encoder);

        assertThat(second).isSameAs(first);
        assertThat(calls).hasValue(1);
    }

    @Test
    public void encodingOfAnotherTypeReplacesTheFirst() {
        final SearchIndexDocument document = document();
        final AtomicInteger integerCalls = new AtomicInteger();
        final AtomicInteger stringCalls = new AtomicInteger();
        final Function<SearchIndexDocument, Integer> integerEncoder = d -> {
            integerCalls.incrementAndGet();
            return 42;
        };

        assertThat(document.encoded(Integer.class, integerEncoder)).isEqualTo(42);
        final String string = document.encoded(String.class, d -> {
            stringCalls.incrementAndGet();
            return "encoded";
        });
        final Integer again = document.encoded(Integer.class, integerEncoder);

        assertThat(string).isEqualTo("encoded");
        assertThat(stringCalls).hasValue(1);
        assertThat(again).isEqualTo(42);
        assertThat(integerCalls).hasValue(2);
    }

    @Test
    public void encodingDoesNotAffectEquality() {
        final SearchIndexDocument encoded = document();
        final SearchIndexDocument plain = document();

        encoded.encoded(String.class, d -> "encoded");

        assertThat(encoded).isEqualTo(plain);
        assertThat(encoded.hashCode()).isEqualTo(plain.hashCode());
        assertThat(encoded.toString()).isEqualTo(plain.toString());
    }

    @Test
    public void encoderReturningNullIsRefusedAndKeepsTheSlotEmpty() {
        final SearchIndexDocument document = document();
        final AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> document.encoded(String.class, d -> null))
                .isInstanceOf(NullPointerException.class);
        final String result = document.encoded(String.class, d -> {
            calls.incrementAndGet();
            return "x";
        });

        assertThat(result).isEqualTo("x");
        assertThat(calls).hasValue(1);
    }

    @Test
    public void failingEncoderKeepsTheSlotEmpty() {
        final SearchIndexDocument document = document();
        final IllegalStateException failure = new IllegalStateException("cannot encode");
        final AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> document.encoded(String.class, d -> {
            throw failure;
        })).isSameAs(failure);
        final String result = document.encoded(String.class, d -> {
            calls.incrementAndGet();
            return "x";
        });

        assertThat(result).isEqualTo("x");
        assertThat(calls).hasValue(1);
    }

    private static SearchIndexDocument document() {
        return SearchIndexDocument.newBuilder(THING_ID)
                .revision(3L)
                .globalRead(List.of("user:alice"))
                .thing(JsonObject.newBuilder().set("thingId", THING_ID.toString()).build())
                .policyAuth(JsonObject.empty())
                .build();
    }

}
