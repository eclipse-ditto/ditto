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
package org.eclipse.ditto.wot.api.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelRefInvalidException;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultWotThingModelExtensionResolver}.
 */
public final class DefaultWotThingModelExtensionResolverTest {

    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    private WotThingModelFetcher thingModelFetcher;
    private WotThingModelExtensionResolver underTest;

    @Before
    public void setUp() {
        thingModelFetcher = mock(WotThingModelFetcher.class);
        underTest = WotThingModelExtensionResolver.of(thingModelFetcher, SAME_THREAD_EXECUTOR);
    }

    @Test
    public void resolveLocalRefInProperty() throws ExecutionException, InterruptedException {
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("genericTemperature", JsonFactory.newObjectBuilder()
                                .set("type", "number")
                                .set("unit", "C")
                                .build())
                        .set("innerTemperature", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/genericTemperature")
                                .set("title", "Inner Temperature")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        // innerTemperature should have type, unit from genericTemperature, plus its own title
        final JsonObject innerTemp = resolved.getProperties().get()
                .getProperty("innerTemperature").get().toJson();
        assertThat(innerTemp.getValue("type")).contains(JsonValue.of("number"));
        assertThat(innerTemp.getValue("unit")).contains(JsonValue.of("C"));
        assertThat(innerTemp.getValue("title")).contains(JsonValue.of("Inner Temperature"));
        // tm:ref should be removed
        assertThat(innerTemp.contains("tm:ref")).isFalse();

        // Verify that no external fetch was attempted
        verify(thingModelFetcher, never()).fetchThingModel(any(IRI.class), any(DittoHeaders.class));
    }

    @Test
    public void resolveLocalRefWithOverrides() throws ExecutionException, InterruptedException {
        // Test that local properties override referenced properties (as per WoT merge semantics)
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("baseProperty", JsonFactory.newObjectBuilder()
                                .set("type", "number")
                                .set("minimum", 0)
                                .set("maximum", 100)
                                .set("description", "Base description")
                                .build())
                        .set("derivedProperty", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/baseProperty")
                                .set("minimum", 10)
                                .set("description", "Overridden description")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        final JsonObject derivedProp = resolved.getProperties().get()
                .getProperty("derivedProperty").get().toJson();
        // Inherited from base
        assertThat(derivedProp.getValue("type")).contains(JsonValue.of("number"));
        assertThat(derivedProp.getValue("maximum")).contains(JsonValue.of(100));
        // Overridden
        assertThat(derivedProp.getValue("minimum")).contains(JsonValue.of(10));
        assertThat(derivedProp.getValue("description")).contains(JsonValue.of("Overridden description"));
    }

    @Test
    public void resolveNestedLocalRefs() throws ExecutionException, InterruptedException {
        // Test transitive local references: A refs B which refs C
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("baseProperty", JsonFactory.newObjectBuilder()
                                .set("type", "integer")
                                .set("unit", "ms")
                                .build())
                        .set("middleProperty", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/baseProperty")
                                .set("minimum", 0)
                                .build())
                        .set("topProperty", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/middleProperty")
                                .set("title", "Top Level")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        final JsonObject topProp = resolved.getProperties().get()
                .getProperty("topProperty").get().toJson();
        // Inherited through the chain
        assertThat(topProp.getValue("type")).contains(JsonValue.of("integer"));
        assertThat(topProp.getValue("unit")).contains(JsonValue.of("ms"));
        assertThat(topProp.getValue("minimum")).contains(JsonValue.of(0));
        assertThat(topProp.getValue("title")).contains(JsonValue.of("Top Level"));
    }

    @Test
    public void resolveCircularLocalRefThrowsException() {
        // Test that circular references are detected and cause an exception
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("propA", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/propB")
                                .set("type", "string")
                                .build())
                        .set("propB", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/propA")
                                .set("type", "number")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);

        assertThatExceptionOfType(WotThingModelRefInvalidException.class)
                .isThrownBy(() -> underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                        .toCompletableFuture().join())
                .satisfies(exception ->
                        assertThat(exception.getDescription()).hasValueSatisfying(desc ->
                                assertThat(desc).contains("Circular reference")));
    }

    @Test
    public void resolveMixedLocalAndExternalRefs() throws ExecutionException, InterruptedException {
        // Test ThingModel with both local and external references
        final JsonObject externalTmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "External Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("externalProp", JsonFactory.newObjectBuilder()
                                .set("type", "boolean")
                                .set("description", "External property")
                                .build())
                        .build())
                .build();

        final ThingModel externalTm = ThingModel.fromJson(externalTmJson);
        when(thingModelFetcher.fetchThingModel(any(IRI.class), any(DittoHeaders.class)))
                .thenReturn(CompletableFuture.completedFuture(externalTm));

        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("localBase", JsonFactory.newObjectBuilder()
                                .set("type", "number")
                                .set("unit", "V")
                                .build())
                        .set("localRef", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/localBase")
                                .set("title", "Local Reference")
                                .build())
                        .set("externalRef", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "https://example.com/model.tm.jsonld#/properties/externalProp")
                                .set("title", "External Reference")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        // Check local reference was resolved
        final JsonObject localRef = resolved.getProperties().get()
                .getProperty("localRef").get().toJson();
        assertThat(localRef.getValue("type")).contains(JsonValue.of("number"));
        assertThat(localRef.getValue("unit")).contains(JsonValue.of("V"));
        assertThat(localRef.getValue("title")).contains(JsonValue.of("Local Reference"));

        // Check external reference was resolved
        final JsonObject externalRef = resolved.getProperties().get()
                .getProperty("externalRef").get().toJson();
        assertThat(externalRef.getValue("type")).contains(JsonValue.of("boolean"));
        assertThat(externalRef.getValue("description")).contains(JsonValue.of("External property"));
        assertThat(externalRef.getValue("title")).contains(JsonValue.of("External Reference"));
    }

    @Test
    public void resolveLocalRefToNonExistentPathThrowsException() {
        // Test that invalid local pointer path causes an exception
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("prop", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/nonExistent")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);

        assertThatExceptionOfType(WotThingModelRefInvalidException.class)
                .isThrownBy(() -> underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                        .toCompletableFuture().join())
                .satisfies(exception ->
                        assertThat(exception.getDescription()).hasValueSatisfying(desc ->
                                assertThat(desc).contains("did not resolve to a value")));
    }

    @Test
    public void resolveLocalRefToAction() throws ExecutionException, InterruptedException {
        // Test local reference from action to another action
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("actions", JsonFactory.newObjectBuilder()
                        .set("baseAction", JsonFactory.newObjectBuilder()
                                .set("input", JsonFactory.newObjectBuilder()
                                        .set("type", "object")
                                        .build())
                                .set("output", JsonFactory.newObjectBuilder()
                                        .set("type", "boolean")
                                        .build())
                                .build())
                        .set("derivedAction", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/actions/baseAction")
                                .set("title", "Derived Action")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        final JsonObject derivedAction = resolved.getActions().get()
                .getAction("derivedAction").get().toJson();
        assertThat(derivedAction.getValue("input")).isPresent();
        assertThat(derivedAction.getValue("output")).isPresent();
        assertThat(derivedAction.getValue("title")).contains(JsonValue.of("Derived Action"));
    }

    @Test
    public void resolveLocalRefToEvent() throws ExecutionException, InterruptedException {
        // Test local reference from event to another event
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("events", JsonFactory.newObjectBuilder()
                        .set("baseEvent", JsonFactory.newObjectBuilder()
                                .set("data", JsonFactory.newObjectBuilder()
                                        .set("type", "string")
                                        .build())
                                .build())
                        .set("derivedEvent", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/events/baseEvent")
                                .set("title", "Derived Event")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        final JsonObject derivedEvent = resolved.getEvents().get()
                .getEvent("derivedEvent").get().toJson();
        assertThat(derivedEvent.getValue("data")).isPresent();
        assertThat(derivedEvent.getValue("title")).contains(JsonValue.of("Derived Event"));
    }

    @Test
    public void resolveThingModelWithNoRefsReturnsUnchanged() throws ExecutionException, InterruptedException {
        // Test that a ThingModel without any tm:ref is returned unchanged
        final JsonObject tmJson = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("@type", "tm:ThingModel")
                .set("title", "Test Model")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("simpleProp", JsonFactory.newObjectBuilder()
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(tmJson);
        final ThingModel resolved = underTest.resolveThingModelRefs(thingModel, DittoHeaders.empty())
                .toCompletableFuture().get();

        assertThat(resolved.toJson()).isEqualTo(thingModel.toJson());
        verify(thingModelFetcher, never()).fetchThingModel(any(IRI.class), any(DittoHeaders.class));
    }
}
