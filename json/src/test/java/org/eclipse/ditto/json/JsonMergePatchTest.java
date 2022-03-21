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
package org.eclipse.ditto.json;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class JsonMergePatchTest {

    @Test
    public void computesDiffForSingleValue() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .build();

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(newValue);
    }

    @Test
    public void computesDiffForSingleValueOutOfMultipleValues() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .set("Bum", "Lux")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .set("Bum", "Lux")
                .build();

        final JsonObject expected = JsonObject.newBuilder()
                .set("Test", "Bar")
                .build();

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
    }

    @Test
    public void computesDiffForMultipleValues() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("Test", "Foo")
                .set("Bum", "Lux")
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("Test", "Bar")
                .set("Bum", "Luxes")
                .build();

        final JsonObject expected = newValue;

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
    }

    @Test
    public void computesDiffForNested() {
        final JsonObject oldValue = JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Foo")
                        .set("Bum", "Lux")
                        .build())
                .build();

        final JsonObject newValue = JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Bar")
                        .set("Bum", "Lux")
                        .build())
                .build();

        final JsonObject expected = JsonObject.newBuilder()
                .set("nested", JsonObject.newBuilder()
                        .set("Test", "Bar")
                        .build())
                .build();;

        assertThat(JsonMergePatch.compute(oldValue, newValue)).contains(expected);
    }

}
