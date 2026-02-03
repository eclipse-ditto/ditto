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
package org.eclipse.ditto.wot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit tests for {@link AtContextPrefixValidator}.
 */
public final class AtContextPrefixValidatorTest {

    @Test
    public void validThingModelWithDefinedPrefixesShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .build())
                        .build())
                .set("title", "Test TM")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("ditto:category", "configuration")
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void validThingModelWithStandardPrefixesShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("tm:ref", "#/properties/base")
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        // "tm" is a standard WoT prefix, should pass
        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void thingModelWithUndefinedPrefixInKeyShouldFail() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("ditto:category", "configuration")
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatExceptionOfType(WotValidationException.class)
                .isThrownBy(thingModel::validateContextPrefixes)
                .withMessageContaining("ditto");
    }

    @Test
    public void thingModelWithUndefinedPrefixInValueShouldFail() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("securityDefinitions", JsonFactory.newObjectBuilder()
                        .set("ace_sc", JsonFactory.newObjectBuilder()
                                .set("scheme", "ace:ACESecurityScheme")
                                .build())
                        .build())
                .set("security", "ace_sc")
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatExceptionOfType(WotValidationException.class)
                .isThrownBy(thingModel::validateContextPrefixes)
                .withMessageContaining("ace");
    }

    @Test
    public void thingModelWithMultipleUndefinedPrefixesShouldReportAll() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("ditto:category", "configuration")
                                .set("custom:field", "value")
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatExceptionOfType(WotValidationException.class)
                .isThrownBy(thingModel::validateContextPrefixes)
                .satisfies(e -> {
                    assertThat(e.getMessage()).contains("ditto");
                    assertThat(e.getMessage()).contains("custom");
                });
    }

    @Test
    public void absoluteUrisShouldNotTriggerPrefixValidation() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("id", "urn:example:thing:123")
                .set("base", "https://example.com/api/things/123")
                .set("links", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder()
                                .set("rel", "service-doc")
                                .set("href", "https://example.com/docs")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void isCurieShouldReturnTrueForValidCuries() {
        assertThat(AtContextPrefixValidator.isCurie("ditto:category")).isTrue();
        assertThat(AtContextPrefixValidator.isCurie("ace:ACESecurityScheme")).isTrue();
        assertThat(AtContextPrefixValidator.isCurie("tm:ref")).isTrue();
        assertThat(AtContextPrefixValidator.isCurie("om2:kilowatt")).isTrue();
    }

    @Test
    public void isCurieShouldReturnFalseForAbsoluteUris() {
        assertThat(AtContextPrefixValidator.isCurie("https://example.com/test")).isFalse();
        assertThat(AtContextPrefixValidator.isCurie("http://example.com/test")).isFalse();
        assertThat(AtContextPrefixValidator.isCurie("urn:example:thing:123")).isFalse();
        assertThat(AtContextPrefixValidator.isCurie("urn:org.eclipse.ditto:thing")).isFalse();
    }

    @Test
    public void isCurieShouldReturnFalseForNonCurieStrings() {
        assertThat(AtContextPrefixValidator.isCurie("simple-string")).isFalse();
        assertThat(AtContextPrefixValidator.isCurie("12:30")).isFalse(); // time format
        assertThat(AtContextPrefixValidator.isCurie("")).isFalse();
        assertThat(AtContextPrefixValidator.isCurie(null)).isFalse();
    }

    @Test
    public void extractPrefixShouldReturnPrefixForValidCuries() {
        assertThat(AtContextPrefixValidator.extractPrefix("ditto:category")).contains("ditto");
        assertThat(AtContextPrefixValidator.extractPrefix("ace:ACESecurityScheme")).contains("ace");
        assertThat(AtContextPrefixValidator.extractPrefix("tm:ref")).contains("tm");
    }

    @Test
    public void extractPrefixShouldReturnEmptyForInvalidInputs() {
        assertThat(AtContextPrefixValidator.extractPrefix("https://example.com")).isEmpty();
        assertThat(AtContextPrefixValidator.extractPrefix("simple-string")).isEmpty();
        assertThat(AtContextPrefixValidator.extractPrefix("")).isEmpty();
        assertThat(AtContextPrefixValidator.extractPrefix(null)).isEmpty();
    }

    @Test
    public void collectUsedPrefixesShouldFindPrefixesInKeysAndValues() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("ditto:category", "status")
                .set("nested", JsonFactory.newObjectBuilder()
                        .set("custom:field", "ace:SomeType")
                        .build())
                .set("array", JsonFactory.newArrayBuilder()
                        .add("om2:kilowatt")
                        .build())
                .build();

        final Set<String> prefixes = AtContextPrefixValidator.collectUsedPrefixes(json);

        assertThat(prefixes).containsExactlyInAnyOrder("ditto", "custom", "ace", "om2");
    }

    @Test
    public void extractDefinedPrefixesShouldExtractFromMultipleContext() {
        final AtContext atContext = MultipleAtContext.of(Arrays.asList(
                SingleUriAtContext.W3ORG_2022_WOT_TD_V11,
                SinglePrefixedAtContext.of("ditto", SingleUriAtContext.DITTO_WOT_EXTENSION),
                SinglePrefixedAtContext.of("ace", SingleUriAtContext.of("http://example.org/ace#"))
        ));

        final Set<String> prefixes = AtContextPrefixValidator.extractDefinedPrefixes(atContext);

        assertThat(prefixes).containsExactlyInAnyOrder("ditto", "ace");
    }

    @Test
    public void extractDefinedPrefixesShouldReturnEmptyForSingleUriContext() {
        final AtContext atContext = SingleUriAtContext.W3ORG_2022_WOT_TD_V11;

        final Set<String> prefixes = AtContextPrefixValidator.extractDefinedPrefixes(atContext);

        assertThat(prefixes).isEmpty();
    }

    @Test
    public void validThingDescriptionWithDefinedPrefixesShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .set("ace", "http://www.example.org/ace-security#")
                                .build())
                        .build())
                .set("id", "urn:example:thing:123")
                .set("title", "Test TD")
                .set("securityDefinitions", JsonFactory.newObjectBuilder()
                        .set("basic_sc", JsonFactory.newObjectBuilder()
                                .set("scheme", "basic")
                                .set("in", "header")
                                .build())
                        .set("ace_sc", JsonFactory.newObjectBuilder()
                                .set("scheme", "ace:ACESecurityScheme")
                                .set("ace:custom", "foobar!")
                                .build())
                        .build())
                .set("security", "basic_sc")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("ditto:category", "configuration")
                                .set("type", "string")
                                .set("forms", JsonFactory.newArrayBuilder()
                                        .add(JsonFactory.newObjectBuilder()
                                                .set("href", "/attributes/status")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        final ThingDescription thingDescription = ThingDescription.fromJson(json);

        assertThatCode(thingDescription::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void allStandardWotPrefixesShouldBeAllowed() {
        // Verify that all standard prefixes are recognized
        assertThat(WotStandardContextPrefixes.isStandardPrefix("td")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("tm")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("jsonschema")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("wotsec")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("hctl")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("htv")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("schema")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("rdfs")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("rdf")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("xsd")).isTrue();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("dct")).isTrue();

        // Non-standard prefixes should not be recognized
        assertThat(WotStandardContextPrefixes.isStandardPrefix("ditto")).isFalse();
        assertThat(WotStandardContextPrefixes.isStandardPrefix("custom")).isFalse();
    }

    @Test
    public void validThingModelWithDeprecationNoticeShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .build())
                        .build())
                .set("title", "Test TM with Deprecation")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("oldProperty", JsonFactory.newObjectBuilder()
                                .set("title", "Old Property (DEPRECATED)")
                                .set("type", "string")
                                .set("ditto:deprecationNotice", JsonFactory.newObjectBuilder()
                                        .set("deprecated", true)
                                        .set("supersededBy", "#/properties/newProperty")
                                        .set("removalVersion", "2.0.0")
                                        .build())
                                .build())
                        .set("newProperty", JsonFactory.newObjectBuilder()
                                .set("title", "New Property")
                                .set("type", "string")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void thingModelWithDeprecationNoticeWithoutContextShouldFail() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", "https://www.w3.org/2022/wot/td/v1.1")
                .set("title", "Test TM")
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("oldProperty", JsonFactory.newObjectBuilder()
                                .set("title", "Old Property (DEPRECATED)")
                                .set("type", "string")
                                .set("ditto:deprecationNotice", JsonFactory.newObjectBuilder()
                                        .set("deprecated", true)
                                        .set("removalVersion", "2.0.0")
                                        .build())
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        // Should fail because "ditto" prefix is not defined in the context
        assertThatExceptionOfType(WotValidationException.class)
                .isThrownBy(thingModel::validateContextPrefixes)
                .withMessageContaining("ditto");
    }

    @Test
    public void validThingModelWithDeprecatedActionShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .build())
                        .build())
                .set("title", "Test TM with Deprecated Action")
                .set("actions", JsonFactory.newObjectBuilder()
                        .set("legacyReset", JsonFactory.newObjectBuilder()
                                .set("title", "Legacy Reset (DEPRECATED)")
                                .set("ditto:deprecationNotice", JsonFactory.newObjectBuilder()
                                        .set("deprecated", true)
                                        .set("removalVersion", "3.0.0")
                                        .build())
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void validThingModelWithDeprecatedEventShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .build())
                        .build())
                .set("title", "Test TM with Deprecated Event")
                .set("events", JsonFactory.newObjectBuilder()
                        .set("oldEvent", JsonFactory.newObjectBuilder()
                                .set("title", "Old Event (DEPRECATED)")
                                .set("ditto:deprecationNotice", JsonFactory.newObjectBuilder()
                                        .set("deprecated", true)
                                        .set("supersededBy", "#/events/newEvent")
                                        .set("removalVersion", "2.0.0")
                                        .build())
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }

    @Test
    public void validThingModelWithDeprecationAtThingLevelShouldPass() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("@context", JsonFactory.newArrayBuilder()
                        .add("https://www.w3.org/2022/wot/td/v1.1")
                        .add(JsonFactory.newObjectBuilder()
                                .set("ditto", "https://ditto.eclipseprojects.io/wot/ditto-extension#")
                                .build())
                        .build())
                .set("title", "Legacy Sensor (DEPRECATED)")
                .set("ditto:deprecationNotice", JsonFactory.newObjectBuilder()
                        .set("deprecated", true)
                        .set("supersededBy", "https://example.com/models/new-sensor-2.0.0.tm.jsonld")
                        .set("removalVersion", "2.0.0")
                        .build())
                .set("properties", JsonFactory.newObjectBuilder()
                        .set("temperature", JsonFactory.newObjectBuilder()
                                .set("type", "number")
                                .build())
                        .build())
                .build();

        final ThingModel thingModel = ThingModel.fromJson(json);

        assertThatCode(thingModel::validateContextPrefixes).doesNotThrowAnyException();
    }
}
