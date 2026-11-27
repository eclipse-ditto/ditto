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
package org.eclipse.ditto.wot.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.devops.ConfigOverrides;
import org.eclipse.ditto.things.model.devops.DynamicValidationConfig;
import org.eclipse.ditto.wot.validation.ValidationContext;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests the {@code alternative-thing-definitions} handling of {@link DefaultTmValidationConfig}.
 */
public final class DefaultTmValidationConfigAlternativesTest {

    private static final String V8 = "https://models.example.org/gateway-8.0.0.tm.jsonld";
    private static final String V9 = "https://models.example.org/gateway-9.0.0.tm.jsonld";
    private static final String V91 = "https://models.example.org/gateway-9.1.0.tm.jsonld";

    @Test
    public void alternativesOfAllMatchingSectionsAreJoined() {
        final DefaultTmValidationConfig underTest = config(
                section("^https://models\\\\.example\\\\.org/gateway-8\\\\.0\\\\.0\\\\.tm\\\\.jsonld$", V9) + "," +
                        section("^https://models\\\\.example\\\\.org/gateway-8\\\\..*$", V91, V9));

        assertThat(underTest.withValidationContext(context(V8)).getAlternativeThingDefinitions())
                .containsExactly(definition(V9), definition(V91));
    }

    @Test
    public void alternativesOfNonMatchingSectionsAreIgnored() {
        final DefaultTmValidationConfig underTest = config(
                section("^https://models\\\\.example\\\\.org/gateway-8\\\\.0\\\\.0\\\\.tm\\\\.jsonld$", V9));

        assertThat(underTest.withValidationContext(context(V9)).getAlternativeThingDefinitions()).isEmpty();
        assertThat(underTest.getAlternativeThingDefinitions()).isEmpty();
    }

    @Test
    public void alternativesAreExposedViaDynamicConfigs() {
        final DefaultTmValidationConfig underTest = config(
                section("^https://models\\\\.example\\\\.org/gateway-8\\\\.0\\\\.0\\\\.tm\\\\.jsonld$", V9));

        assertThat(underTest.getDynamicConfigs())
                .singleElement()
                .extracting(DynamicValidationConfig::getConfigOverrides)
                .satisfies(configOverrides -> assertThat(configOverrides.map(
                        ConfigOverrides::getAlternativeThingDefinitions).orElseThrow())
                        .containsExactly(definition(V9)));
    }

    @Test
    public void sectionWithAlternativesButWithoutThingDefinitionPatternsIsRejected() {
        assertThatExceptionOfType(DittoConfigError.class).isThrownBy(() -> config(
                "{ validation-context.ditto-headers-patterns = [{ ditto-originator = \"^pre:ditto$\" }]\n" +
                        "  config-overrides.alternative-thing-definitions = [\"" + V9 + "\"] }"));
    }

    @Test
    public void alternativeWhichIsNoUrlIsRejected() {
        assertThatExceptionOfType(DittoConfigError.class).isThrownBy(() -> config(
                section("^.*$", "org.example:gateway:9.0.0")));
    }

    private static DefaultTmValidationConfig config(final String dynamicConfiguration) {
        return DefaultTmValidationConfig.of(ConfigFactory.parseString(
                "tm-model-validation.dynamic-configuration = [" + dynamicConfiguration + "]"));
    }

    private static String section(final String thingDefinitionPattern, final String... alternatives) {
        return "{ validation-context.thing-definition-patterns = [\"" + thingDefinitionPattern + "\"]\n" +
                "  config-overrides.alternative-thing-definitions = [\"" + String.join("\",\"", alternatives) +
                "\"] }";
    }

    private static ValidationContext context(final String thingDefinition) {
        return ValidationContext.buildValidationContext(DittoHeaders.empty(), definition(thingDefinition));
    }

    private static ThingDefinition definition(final String definition) {
        return ThingsModelFactory.newDefinition(definition);
    }

}
