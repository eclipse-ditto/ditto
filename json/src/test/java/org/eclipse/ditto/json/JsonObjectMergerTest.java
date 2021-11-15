/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class JsonObjectMergerTest {

    private static JsonObject merge(final JsonObject object1, final JsonObject object2) {
        return JsonObjectMerger.mergeJsonObjects(object1, object2);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonObjectMerger.class, areImmutable());
    }

    @Test
    public void emptyObjectsMergeToEmptyObject() {
        final JsonObject object1 = JsonFactory.newObject();
        final JsonObject object2 = JsonFactory.newObject();

        Assertions.assertThat(merge(object1, object2)).isEmpty();
    }

    @Test
    public void retainExtraFieldFromObject1() {
        final JsonObject object1 = JsonFactory.newObjectBuilder().set("x", 5).build();
        final JsonObject object2 = JsonFactory.newObject();

        Assertions.assertThat(merge(object1, object2)).isEqualTo(object1);
    }

    @Test
    public void retainExtraFieldFromObject2() {
        final JsonObject object1 = JsonFactory.newObject();
        final JsonObject object2 = JsonFactory.newObjectBuilder().set("y", 6).build();

        Assertions.assertThat(merge(object1, object2)).isEqualTo(object2);
    }

    @Test
    public void mergeFieldsFromBothObjects() {
        final JsonObject object1 = JsonFactory.newObjectBuilder().set("x", 5).build();
        final JsonObject object2 = JsonFactory.newObjectBuilder().set("y", 6).build();
        final JsonObject mergedObject = JsonFactory.newObjectBuilder()
                .set("x", 5)
                .set("y", 6)
                .build();

        Assertions.assertThat(merge(object1, object2)).isEqualTo(mergedObject);
    }

    @Test
    public void mergeFieldsWithArrays() {
        final JsonObject object1 = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("x", 5).build())
                        .add(JsonFactory.newArray())
                        .build())
                .build();

        final JsonObject object2 = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("z", 5).build())
                        .add(42)
                        .build())
                .set("b", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("y", 6).build())
                        .add(JsonFactory.newObjectBuilder().set("z", 9).build())
                        .add(7)
                        .build())
                .build();

        final JsonObject mergedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("x", 5).build())
                        .add(JsonFactory.newArray())
                        .build())
                .set("b", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("y", 6).build())
                        .add(JsonFactory.newObjectBuilder().set("z", 9).build())
                        .add(7)
                        .build())
                .build();

        Assertions.assertThat(merge(object1, object2)).isEqualTo(mergedObject);
    }

    @Test
    public void testMergeNullObjects() {
        final JsonObject mergedObject = merge(JsonFactory.nullObject(), JsonFactory.nullObject());

        Assertions.assertThat(mergedObject).isEqualTo(JsonFactory.nullObject());
    }

}
