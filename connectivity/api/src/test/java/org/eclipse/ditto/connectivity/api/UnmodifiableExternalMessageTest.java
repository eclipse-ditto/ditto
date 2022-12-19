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
package org.eclipse.ditto.connectivity.api;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.EnforcementFilter;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.TopicPath;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link UnmodifiableExternalMessage}.
 */
public final class UnmodifiableExternalMessageTest {

    private static final String PAYLOAD = "payload";
    private static final byte[] BYTES = PAYLOAD.getBytes(StandardCharsets.UTF_8);

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        // The field "bytePayload" is mutable.
        // Assume the user never modifies it.
        MutabilityAssert.assertInstancesOf(
                UnmodifiableExternalMessage.class,
                areImmutable(),
                provided(
                                Adaptable.class,
                                AuthorizationContext.class,
                                ByteBuffer.class,
                                DittoHeaders.class,
                                EnforcementFilter.class,
                                HeaderMapping.class,
                                PayloadMapping.class,
                                Source.class,
                                TopicPath.class
                        )
                        .areAlsoImmutable(),
                assumingFields("bytePayload").areNotModifiedAndDoNotEscape(),
                assumingFields("headers").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(UnmodifiableExternalMessage.class)
                .withPrefabValues(ByteBuffer.class,
                        ByteBuffer.wrap("red".getBytes()),
                        ByteBuffer.wrap("black".getBytes()))
                .usingGetClass()
                .verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void builderAndGettersWorkAsExpected() {
        final var initialHeaders = Map.of("eclipse", "ditto");
        final var authorizationContext = Mockito.mock(AuthorizationContext.class);
        final var topicPath = Mockito.mock(TopicPath.class);
        final var enforcementFilter = (EnforcementFilter<Signal<?>>) Mockito.mock(EnforcementFilter.class);
        final var headerMapping = Mockito.mock(HeaderMapping.class);
        final var payloadMapping = Mockito.mock(PayloadMapping.class);
        final var sourceAddress = "mySource";
        final var source = Mockito.mock(Source.class);
        final var internalHeaders = DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .build();

        final var externalMessage = UnmodifiableExternalMessage.newBuilder(initialHeaders)
                .withAdditionalHeaders("ditto", "eclipse")
                .withTextAndBytes(PAYLOAD, BYTES)
                .withAuthorizationContext(authorizationContext)
                .withTopicPath(topicPath)
                .withEnforcement(enforcementFilter)
                .withHeaderMapping(headerMapping)
                .withPayloadMapping(payloadMapping)
                .withSourceAddress(sourceAddress)
                .withSource(source)
                .withInternalHeaders(internalHeaders)
                .build();

        final var externalMessageCopy = UnmodifiableExternalMessage.newBuilder(externalMessage).build();

        softly.assertThat(externalMessage.getHeaders())
                .as("headers")
                .containsOnly(Map.entry("eclipse", "ditto"), Map.entry("ditto", "eclipse"));
        softly.assertThat(externalMessage.isTextMessage()).as("is text message").isTrue();
        softly.assertThat(externalMessage.isBytesMessage()).as("is bytes message").isTrue();
        softly.assertThat(externalMessage.getTextPayload()).as("text payload").hasValue(PAYLOAD);
        softly.assertThat(externalMessage.getBytePayload()).as("byte payload").hasValue(ByteBuffer.wrap(BYTES));
        softly.assertThat(externalMessage.getPayloadType())
                .as("payload type")
                .isEqualTo(ExternalMessage.PayloadType.TEXT_AND_BYTES);
        softly.assertThat(externalMessage.getAuthorizationContext())
                .as("authorization context")
                .hasValue(authorizationContext);
        softly.assertThat(externalMessage.getTopicPath()).as("topic path").hasValue(topicPath);
        softly.assertThat(externalMessage.getEnforcementFilter()).as("enforcement filter").hasValue(enforcementFilter);
        softly.assertThat(externalMessage.getHeaderMapping()).as("header mapping").hasValue(headerMapping);
        softly.assertThat(externalMessage.getPayloadMapping()).as("payload mapping").hasValue(payloadMapping);
        softly.assertThat(externalMessage.getSourceAddress()).as("source address").hasValue(sourceAddress);
        softly.assertThat(externalMessage.getSource()).as("source").hasValue(source);
        softly.assertThat(externalMessage.getInternalHeaders()).as("internal headers").isEqualTo(internalHeaders);
        softly.assertThat(externalMessageCopy).as("copy builder").isEqualTo(externalMessage);
    }

}
