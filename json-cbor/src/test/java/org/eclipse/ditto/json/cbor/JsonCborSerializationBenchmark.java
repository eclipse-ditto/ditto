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
package org.eclipse.ditto.json.cbor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Measures building, CBOR serialization and parsing of a Thing-like JSON object nested several levels deep, as
 * signals carry it through the cluster. Run with {@code -prof gc} to see the allocation per operation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(1)
public class JsonCborSerializationBenchmark {

    private static final int FEATURE_COUNT = 6;

    private final JacksonCborFactory cborFactory = new JacksonCborFactory();
    private final ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);

    private JsonObject thingJson;
    private byte[] thingCbor;
    private String thingJsonString;

    @Setup
    public void setup() throws IOException {
        thingJson = buildThing();
        thingCbor = cborFactory.toByteArray(buildThing());
        thingJsonString = buildThing().toString();
    }

    /**
     * Builds a fresh Thing JSON, like a signal's {@code toJson()} does.
     */
    @Benchmark
    public JsonObject buildThingJson() {
        return buildThing();
    }

    /**
     * Builds a fresh Thing JSON and serializes it into a buffer, like sending a signal through the cluster.
     */
    @Benchmark
    public ByteBuffer buildAndSerializeThingJson() throws IOException {
        buffer.clear();
        cborFactory.writeToByteBuffer(buildThing(), buffer);
        return buffer;
    }

    /**
     * Serializes the same, already built Thing JSON again and again.
     */
    @Benchmark
    public ByteBuffer serializeSameThingJson() throws IOException {
        buffer.clear();
        cborFactory.writeToByteBuffer(thingJson, buffer);
        return buffer;
    }

    /**
     * Parses the Thing from CBOR, like receiving a signal from the cluster.
     */
    @Benchmark
    public JsonValue parseThingCbor() {
        return cborFactory.readFrom(thingCbor);
    }

    /**
     * Parses the Thing from CBOR and serializes it again, like forwarding a received signal.
     */
    @Benchmark
    public ByteBuffer parseThingCborAndSerialize() throws IOException {
        buffer.clear();
        cborFactory.writeToByteBuffer(cborFactory.readFrom(thingCbor), buffer);
        return buffer;
    }

    /**
     * Parses the Thing from a JSON string.
     */
    @Benchmark
    public JsonValue parseThingJsonString() {
        return JsonFactory.readFrom(thingJsonString);
    }

    private static JsonObject buildThing() {
        final JsonObject room = JsonObject.newBuilder()
                .set("id", "room-12")
                .set("name", "Kitchen")
                .set("tags", JsonArray.of(JsonValue.of("ground-floor"), JsonValue.of("heated")))
                .build();
        final JsonObject attributes = JsonObject.newBuilder()
                .set("type", "temperature-sensor")
                .set("serialNumber", "SN-0001-4711")
                .set("location", JsonObject.newBuilder()
                        .set("building", "building-1")
                        .set("floor", 3)
                        .set("room", room)
                        .build())
                .build();
        final JsonObjectBuilder features = JsonObject.newBuilder();
        for (int i = 0; i < FEATURE_COUNT; i++) {
            final JsonObject status = JsonObject.newBuilder()
                    .set("value", 21.5 + i)
                    .set("timestamp", "2026-10-29T17:27:46.537Z")
                    .set("quality", JsonObject.newBuilder()
                            .set("rssi", -71 - i)
                            .set("snr", 7.5)
                            .build())
                    .build();
            final JsonObject configuration = JsonObject.newBuilder()
                    .set("interval", 900)
                    .set("thresholds", JsonArray.of(JsonValue.of(10), JsonValue.of(20), JsonValue.of(30)))
                    .build();
            features.set("feature-" + i, JsonObject.newBuilder()
                    .set("definition", JsonArray.of(
                            JsonValue.of("https://models.example.com/feature-" + i + "-1.0.0.tm.jsonld")))
                    .set("properties", JsonObject.newBuilder()
                            .set("status", status)
                            .set("configuration", configuration)
                            .build())
                    .build());
        }
        return JsonObject.newBuilder()
                .set("thingId", "org.eclipse.ditto:device-4711")
                .set("policyId", "org.eclipse.ditto:device-4711")
                .set("definition", "https://models.example.com/sensor-1.0.0.tm.jsonld")
                .set("attributes", attributes)
                .set("features", features.build())
                .set("_revision", 4711L)
                .set("_modified", "2026-10-29T17:27:46.537Z")
                .build();
    }

    public static void main(final String[] args) throws RunnerException {
        final Options opt = new OptionsBuilder()
                .include(JsonCborSerializationBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

}
