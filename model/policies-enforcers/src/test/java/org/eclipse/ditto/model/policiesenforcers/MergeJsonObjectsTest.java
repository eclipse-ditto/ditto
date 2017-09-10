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
package org.eclipse.ditto.model.policiesenforcers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link JsonObjectMerger}.
 */
public final class MergeJsonObjectsTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonObjectMerger.class, areImmutable());
    }

    @Test
    public void emptyObjectsMergeToEmptyObject() {
        final JsonObject object1 = JsonFactory.newObject();
        final JsonObject object2 = JsonFactory.newObject();

        assertThat(merge(object1, object2)).isEmpty();
    }

    @Test
    public void retainExtraFieldFromObject1() {
        final JsonObject object1 = JsonFactory.newObjectBuilder().set("x", 5).build();
        final JsonObject object2 = JsonFactory.newObject();

        assertThat(merge(object1, object2)).isEqualTo(object1);
    }

    @Test
    public void retainExtraFieldFromObject2() {
        final JsonObject object1 = JsonFactory.newObject();
        final JsonObject object2 = JsonFactory.newObjectBuilder().set("y", 6).build();

        assertThat(merge(object1, object2)).isEqualTo(object2);
    }

    @Test
    public void mergeFieldsFromBothObjects() {
        final JsonObject object1 = JsonFactory.newObjectBuilder().set("x", 5).build();
        final JsonObject object2 = JsonFactory.newObjectBuilder().set("y", 6).build();
        final JsonObject mergedObject = JsonFactory.newObjectBuilder()
                .set("x", 5)
                .set("y", 6)
                .build();

        assertThat(merge(object1, object2)).isEqualTo(mergedObject);
    }

    @Test
    public void mergeFieldsInsideArrays() {
        final JsonObject object1 = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("x", 5).build())
                        .add(JsonFactory.newArray())
                        .build())
                .build();

        final JsonObject object2 = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("y", 6).build())
                        .add(JsonFactory.newObjectBuilder().set("z", 9).build())
                        .add(7)
                        .build())
                .build();

        final JsonObject mergedObject = JsonFactory.newObjectBuilder()
                .set("a", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder().set("x", 5).set("y", 6).build())
                        .add(JsonFactory.newArray())
                        .add(7)
                        .build())
                .build();

        assertThat(merge(object1, object2)).isEqualTo(mergedObject);
    }

    private static JsonObject merge(final JsonObject object1, final JsonObject object2) {
        final BiFunction<JsonObject, JsonObject, JsonObject> underTest = new JsonObjectMerger();
        return underTest.apply(object1, object2);
    }

}
