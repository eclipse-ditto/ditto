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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.internal.utils.persistence.mongo.JsonValueToDbEntityMapper.forJsonObject;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.Function;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link JsonValueToDbEntityMapper}.
 */
public final class JsonValueToDbEntityMapperTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonValueToDbEntityMapper.class, areImmutable(), provided(Function.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetMapperForJsonObjectWithNullKeyNameReviser() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> forJsonObject(null))
                .withMessage("The %s must not be null!", "JSON key name reviser")
                .withNoCause();
    }

    @Test
    public void tryToTestNullJsonObject() {
        final Function<String, String> jsonKeyNameReviser = Mockito.mock(Function.class);
        final Function<JsonObject, BsonDocument> underTest =
                JsonValueToDbEntityMapper.forJsonObject(jsonKeyNameReviser);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The %s must not be null!", "JSON object to be mapped")
                .withNoCause();
    }

    @Test
    public void mapJsonObjectReturnsExpectedBsonDocument() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set("foo", JsonFactory.newObjectBuilder()
                    .set("org.eclipse.ditto", 42)
                    .build())
                .set("something$", JsonFactory.newArrayBuilder()
                    .add(23.42)
                    .add(true)
                    .add("bar")
                    .build())
                .set(JsonFactory.newKey("/with/slash"), 12345491014314L)
                .set("nada", JsonFactory.nullObject())
                .build();

        final BsonDocument expectedBsonDocument = BsonDocument.parse(jsonObject.toString());
        final Function<String, String> jsonKeyNameReviser = s -> s;
        final Function<JsonObject, BsonDocument> underTest =
                JsonValueToDbEntityMapper.forJsonObject(jsonKeyNameReviser);

        final BsonDocument actualBasicDocument = underTest.apply(jsonObject);

        assertThat(actualBasicDocument).isEqualTo(expectedBsonDocument);
    }

}
