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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.utils.persistence.mongo.JsonValueToDbEntityMapper.forJsonObject;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.function.Function;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;
import org.mockito.Mockito;

import com.mongodb.BasicDBObject;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.JsonValueToDbEntityMapper}.
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
        final Function<JsonObject, BasicDBObject> underTest =
                JsonValueToDbEntityMapper.forJsonObject(jsonKeyNameReviser);

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The %s must not be null!", "JSON object to be mapped")
                .withNoCause();
    }

    @Test
    public void mapJsonObjectReturnsExpectedBasicDBObject() {
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

        final BasicDBObject expectedBasicDBObject = BasicDBObject.parse(jsonObject.toString());
        final Function<String, String> jsonKeyNameReviser = s -> s;
        final Function<JsonObject, BasicDBObject> underTest =
                JsonValueToDbEntityMapper.forJsonObject(jsonKeyNameReviser);

        final BasicDBObject actualBasicDBObject = underTest.apply(jsonObject);

        assertThat(actualBasicDBObject).isEqualTo(expectedBasicDBObject);
    }

}