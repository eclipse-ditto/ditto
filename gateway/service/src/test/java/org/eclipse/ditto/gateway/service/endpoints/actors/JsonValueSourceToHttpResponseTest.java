/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.HttpEncodings;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.ByteString;

/**
 * Unit test for {@link JsonValueSourceToHttpResponse}.
 */
public final class JsonValueSourceToHttpResponseTest {

    private static ActorSystem actorSystem;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void setUpClass() {
        actorSystem = ActorSystem.create(JsonValueSourceToHttpResponseTest.class.getSimpleName());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonValueSourceToHttpResponse.class, areImmutable());
    }

    @Test
    public void applyNullSource() {
        assertThatNullPointerException()
                .isThrownBy(() -> JsonValueSourceToHttpResponse.getInstance().apply(null))
                .withMessage("The source must not be null!")
                .withNoCause();
    }

    @Test
    public void getHttpResponseForEmptySource() {
        final var underTest = JsonValueSourceToHttpResponse.getInstance();

        final var httpResponse = underTest.apply(Source.empty());

        softly.assertThat(httpResponse.status()).as("status code").isEqualTo(StatusCodes.OK);
        softly.assertThat(httpResponse.isResponse()).as("is response").isTrue();
        softly.assertThat(httpResponse.encoding()).as("identity encoding").isEqualTo(HttpEncodings.IDENTITY);

        final var entity = httpResponse.entity();

        softly.assertThat(entity.getContentType())
                .as("content-type")
                .isEqualTo(JsonValueSourceToHttpResponse.CONTENT_TYPE_NDJSON);
        softly.assertThat(entity.isChunked()).as("is chunked").isTrue();

        final var responseBodyAsJsonValue = readAsJsonValueList(getAsJavaStream(entity.getDataBytes()));

        softly.assertThat(responseBodyAsJsonValue).as("response body is empty").isEmpty();
    }

    @Test
    public void getHttpResponseForSourceWithTwoJsonObject() {
        final var jsonObjectOne = JsonObject.newBuilder()
                .set("type", "quoteOfTheDay")
                .set("value", JsonObject.newBuilder()
                        .set("date", JsonObject.newBuilder()
                                .set("year", 2014)
                                .set("month", 2)
                                .set("day", 22)
                                .build())
                        .set("quote", "Every man takes the limits of his own field of vision for the limits of the" +
                                " world.")
                        .set("origin", "Arthur Schopenhauer")
                        .set("makesSense", true)
                        .build())
                .build();
        final var jsonObjectTwo = JsonObject.newBuilder()
                .set("type", "quoteOfTheDay")
                .set("value", JsonObject.newBuilder()
                        .set("date", JsonObject.newBuilder()
                                .set("year", 2004)
                                .set("month", 2)
                                .set("day", 22)
                                .build())
                        .set("quote", "The saddest aspect of life right now is that science gathers knowledge faster" +
                                " than society gathers wisdom.")
                        .set("origin", "Isaac Asimov")
                        .set("makesSense", true)
                        .build())
                .build();
        final var jsonObjects = List.of(jsonObjectOne, jsonObjectTwo);
        final var underTest = JsonValueSourceToHttpResponse.getInstance();

        final var httpResponse = underTest.apply(Source.from(jsonObjects).map(JsonValue::of));
        final var responseEntity = httpResponse.entity();
        final var jsonObjectsFromHttpResponse = readAsJsonValueList(getAsJavaStream(responseEntity.getDataBytes()));

        assertThat(jsonObjectsFromHttpResponse).isEqualTo(jsonObjects);
    }

    private static Stream<ByteString> getAsJavaStream(final Source<ByteString, Object> entityDataBytes) {
        final var systemMaterializer = SystemMaterializer.get(actorSystem);
        return entityDataBytes.runWith(StreamConverters.asJavaStream(), systemMaterializer.materializer());
    }

    private static List<JsonValue> readAsJsonValueList(final Stream<ByteString> byteStringStream) {
        final List<JsonValue> result = new ArrayList<>();
        byteStringStream.map(ByteString::utf8String)
                .forEach(s -> {
                     if (!"\n".equals(s)) {
                         result.add(JsonFactory.readFrom(s));
                     }
                });

        return result;
    }

}
