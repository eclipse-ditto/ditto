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
package org.eclipse.ditto.internal.utils.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;
import java.util.regex.Pattern;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TraceInformationGenerator}.
 */
public final class TraceInformationGeneratorTest {

    private TraceInformationGenerator underTest;

    @Before
    public void before() {
        underTest = TraceInformationGenerator.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(TraceInformationGenerator.class,
                areImmutable(),
                // according to JavaDoc, Pattern is immutable!
                provided(Pattern.class).isAlsoImmutable());
    }

    @Ignore("TraceInformationGenerator is a Singleton and thus always equal to itself")
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TraceInformationGenerator.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceReturnsSingleInstance() {
        assertThat(TraceInformationGenerator.getInstance()).isSameAs(TraceInformationGenerator.getInstance());
    }

    @Test
    public void api2FeaturePropertyUpdate() {
        final var traceUri = URI.create("/api/2/things" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);
        final var sanitizedUri = URI.create(traceUri + "/features" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);

        final var traceInformation = underTest.apply(
                "/api/2/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features/Vehicle/properties/Engine/max-speed"
        );

        assertThat(traceInformation)
                .isEqualTo(TraceInformation.newInstance(traceUri, TagSet.ofTag(getRequestUriTag(sanitizedUri))));
    }

    private static Tag getRequestUriTag(final URI requestUri) {
        return SpanTagKey.REQUEST_URI.getTagForValue(requestUri);
    }

    @Test
    public void api2FeatureDefinitionUpdate() {
        final var traceUri = URI.create("/api/2/things" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);
        final var sanitizedUri = URI.create(traceUri + "/features" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);

        final var traceInformation =
                underTest.apply("/api/2/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features/Vehicle/definition");

        assertThat(traceInformation)
                .isEqualTo(TraceInformation.newInstance(traceUri, TagSet.ofTag(getRequestUriTag(sanitizedUri))));
    }

    @Test
    public void api2ThingsUriIsShortened() {
        final var expectedUri = URI.create("/api/2/things" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);

        assertThat(underTest.apply("/api/2/things/ns:1"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void api2ThingsSearchUriIsShortened() {
        final var expectedUri = URI.create("/api/2/search/things" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);

        assertThat(underTest.apply("/api/2/search/things"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void api2ThingsSearchCountUriIsShortened() {
        final var expectedUri = URI.create("/api/2/search/things" + TraceInformationGenerator.SHORTENED_PATH_SUFFIX);

        assertThat(underTest.apply("/api/2/search/things/count"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void statusUriRemainsTheSame() {
        final var expectedUri = URI.create("/status");

        assertThat(underTest.apply("/status"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void statusHealthUriRemainsTheSame() {
        final var expectedUri = URI.create("/status/health");

        assertThat(underTest.apply("/status/health"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void nonExistingStatusSubUriReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/status/bumlux"))
               .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void nonExistingUriStartingWithStatusReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/status1"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void onExistingUriStartingWithStatusHealthReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/status/healthTest"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void nonExistingApiSubUriReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/api/9/search/things"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void nonExistingApiSubUriStartingWithThingsReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/api/9/search/thingsX"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

    @Test
    public void nonExistingRootSubUriReturnsFallback() {
        final var expectedUri = TraceInformationGenerator.FALLBACK_URI;

        assertThat(underTest.apply("/bumlux"))
                .isEqualTo(TraceInformation.newInstance(expectedUri, TagSet.ofTag(getRequestUriTag(expectedUri))));
    }

}
