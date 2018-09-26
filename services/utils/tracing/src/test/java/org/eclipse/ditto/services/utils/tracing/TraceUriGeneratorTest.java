/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.utils.tracing.TraceUriGenerator.FALLBACK_PATH;
import static org.eclipse.ditto.services.utils.tracing.TraceUriGenerator.MESSAGES_PATH_SUFFIX;
import static org.eclipse.ditto.services.utils.tracing.TraceUriGenerator.SHORTENED_PATH_SUFFIX;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.tracing.TraceUriGenerator}.
 */
public final class TraceUriGeneratorTest {

    private static final TraceUriGenerator UNDER_TEST = TraceUriGenerator.getInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TraceUriGenerator.class,
                areImmutable(),
                // according to JavaDoc, Pattern is immutable!
                provided(Pattern.class).isAlsoImmutable());
    }

    @Ignore("TraceUriGenerator is a Singleton and thus always equal to itself")
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TraceUriGenerator.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void api1ThingsMessagesUriReturnsCorrectSuffix() {
        final String path =
                "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/inbox/messages/randomMessageSubject";
        final String expectedUri = "/api/1/things" + MESSAGES_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1FeatureUpdate() {
        final String path = "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features/Vehicle";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/features" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1FeaturesUpdate() {
        final String path = "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/features" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1AttributesUpdate() {
        final String path = "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/attributes";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/attributes" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1AttributeUpdate() {
        final String path = "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/attributes/firmware-version";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/attributes" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api2FeaturePropertyUpdate() {
        final String path =
                "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features/Vehicle/properties/Engine/max-speed";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/features" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api2FeatureDefinitionUpdate() {
        final String path = "/api/1/things/abc:1a4ed3df-308b-462e-9cfc-b78891f18c39/features/Vehicle/definition";
        final String expectedUri = "/api/1/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri + "/features" + SHORTENED_PATH_SUFFIX;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1ThingsClaimMessagesUriReturnsCorrectSuffix() {
        final String path = "api/1/things/abcdefgh:fancy-car/inbox/claim";
        final String expectedUri = "/api/1/things" + MESSAGES_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api1MessageToFeature() {
        final String path = "api/1/things/abcdefgh:fancy-car/features/Vehicle/inbox/messages/shutdown";
        final String expectedUri = "/api/1/things" + MESSAGES_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api2ThingsUriIsShortened() {
        final String path = "/api/2/things/ns:1";
        final String expectedUri = "/api/2/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api2ThingsSearchUriIsShortened() {
        final String path = "/api/2/search/things";
        final String expectedUri = "/api/2/search/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void api2ThingsSearchCountUriIsShortened() {
        final String path = "/api/2/search/things/count";
        final String expectedUri = "/api/2/search/things" + SHORTENED_PATH_SUFFIX;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void statusUriRemainsTheSame() {
        final String path = "/status";
        final String expectedUri = "/status";
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void statusHealthUriRemainsTheSame() {
        final String path = "/status/health";
        final String expectedUri = "/status/health";
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingStatusSubUriReturnsFallback() {
        final String path = "/status/bumlux";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingUriStartingWithStatusReturnsFallback() {
        final String path = "/status1";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void onExistingUriStartingWithStatusHealthReturnsFallback() {
        final String path = "/status/healthTest";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingApiSubUriReturnsFallback() {
        final String path = "/api/9/search/things";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingApiSubUriStartingWithThingsReturnsFallback() {
        final String path = "/api/9/search/thingsX";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingApi2SubUriReturnsFallback() {
        final String path = "/api/2/bumlux";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingSearchSubUriReturnsFallback() {
        final String path = "/api/2/search/bumlux";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    @Test
    public void nonExistingRootSubUriReturnsFallback() {
        final String path = "/bumlux";
        final String expectedUri = FALLBACK_PATH;
        final String expectedPath = expectedUri;
        assertTraceUri(path,
                TraceInformation.Builder.forTraceUri(expectedUri)
                        .tag(TracingTags.REQUEST_PATH, expectedPath)
                        .build());
    }

    private static void assertTraceUri(final String requestUri,
            final TraceInformation expectedTraceInformation) {
        assertThat(UNDER_TEST.apply(requestUri)).isEqualTo(expectedTraceInformation);
    }

}
