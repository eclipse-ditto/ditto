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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.gateway.service.streaming.signals.Jwt;
import org.eclipse.ditto.gateway.service.streaming.signals.StartStreaming;
import org.eclipse.ditto.gateway.service.streaming.signals.StopStreaming;
import org.eclipse.ditto.gateway.service.streaming.signals.StreamControlMessage;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

/**
 * Unit test for {@link ProtocolMessageExtractor}.
 */
@RunWith(Enclosed.class)
public final class ProtocolMessageExtractorTest {

    public static final class GeneralFunctionalityTest {

        private final String connectionCorrelationId = String.valueOf(UUID.randomUUID());
        private ProtocolMessageExtractor underTest;

        @Before
        public void setUp() {
            underTest = new ProtocolMessageExtractor(Mockito.mock(AuthorizationContext.class), connectionCorrelationId);
        }

        @Test
        public void noneProtocolMessagesMappedToNull() {
            try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
                softly.assertThat(underTest.apply(null))
                        .as("apply null")
                        .isEmpty();
                softly.assertThat(underTest.apply(""))
                        .as("apply empty")
                        .isEmpty();
                softly.assertThat(underTest.apply("{\"some\":\"json\"}"))
                        .as("apply some JSON")
                        .isEmpty();
            }
        }

        @Test
        public void extractJwtProtocolMessage() {
            final String jwt = getJwt();
            final String jwtProtocolMessage = "JWT-TOKEN?jwtToken=" + jwt;
            final StreamControlMessage expected = Jwt.newInstance(jwt, connectionCorrelationId);

            assertThat(underTest.apply(jwtProtocolMessage)).contains(expected);
        }

        private static String getJwt() {
            final String header = getBase64EncodedString(JsonObject.newBuilder()
                    .set("header", "value")
                    .build());
            final String payload = getBase64EncodedString(JsonObject.newBuilder()
                    .set("exp", Instant.now().plusSeconds(60).getEpochSecond())
                    .build());
            final String signature = getBase64EncodedString(JsonObject.newBuilder()
                    .set("signature", "foo")
                    .build());

            return String.format("%s.%s.%s", header, payload, signature);
        }

        private static String getBase64EncodedString(final JsonObject jsonObject) {
            final Base64.Encoder encoder = Base64.getEncoder();
            final String jsonString = jsonObject.toString();
            return new String(encoder.encode(jsonString.getBytes()));
        }

    }

    @RunWith(Parameterized.class)
    public static final class StartSendingTest {

        @Parameterized.Parameter
        public ProtocolMessageType protocolMessageType;

        private StreamingType streamingType;
        private String correlationId;
        private AuthorizationContext authContext;

        private ProtocolMessageExtractor underTest;

        @Parameterized.Parameters(name = "{0}")
        public static List<ProtocolMessageType> startSendingProtocolMessageTypes() {
            return Arrays.stream(ProtocolMessageType.values())
                    .filter(ProtocolMessageType::isStartSending)
                    .collect(Collectors.toList());
        }

        @Before
        public void setUp() {
            streamingType = protocolMessageType.getStreamingTypeOrThrow();
            correlationId = String.valueOf(UUID.randomUUID());
            authContext = Mockito.mock(AuthorizationContext.class);
            underTest = new ProtocolMessageExtractor(authContext, correlationId);
        }

        @Test
        public void startSending() {
            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext).build();

            assertThat(underTest.apply(protocolMessageType.toString())).contains(expected);
        }

        @Test
        public void startSendingWithNamespaces() {
            final Collection<String> namespaces = Arrays.asList("eclipse", "ditto", "is", "awesome");
            final StartStreaming expected = StartStreaming.getBuilder(streamingType, correlationId, authContext)
                    .withNamespaces(namespaces)
                    .build();

            final String requestParams = MessageFormat.format("?namespaces={0}", String.join(",", namespaces));

            assertThat(underTest.apply(protocolMessageType + requestParams)).contains(expected);
        }

        @Test
        public void startSendingWithFilter() {
            final String filter = "eq(foo,1)";
            final StartStreaming expected = StartStreaming.getBuilder(streamingType, correlationId, authContext)
                    .withFilter(filter)
                    .build();

            final String requestParams = MessageFormat.format("?filter={0}", filter);

            assertThat(underTest.apply(protocolMessageType + requestParams)).contains(expected);
        }

        @Test
        public void startSendingWithEmptyFilter() {
            final StartStreaming expected = StartStreaming.getBuilder(streamingType, correlationId, authContext)
                    .withFilter("")
                    .build();

            assertThat(underTest.apply(protocolMessageType + "?filter=")).contains(expected);
        }

        @Test
        public void startSendingWithEmptyNamespace() {
            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext).build();

            assertThat(underTest.apply(protocolMessageType + "?namespaces=")).contains(expected);
        }

        @Test
        public void startSendingWithExtraFields() {
            final ThingFieldSelector extraFields = ThingFieldSelector.fromJsonFieldSelector(
                    JsonFieldSelector.newInstance("attributes", "features/location"));
            final StartStreaming expected = StartStreaming.getBuilder(streamingType, correlationId, authContext)
                    .withExtraFields(extraFields)
                    .build();

            final String requestParams = MessageFormat.format("?extraFields={0}", extraFields.toString());

            assertThat(underTest.apply(protocolMessageType + requestParams)).contains(expected);
        }

        @Test
        public void startSendingWithEmptyExtraFields() {
            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext).build();

            assertThat(underTest.apply(protocolMessageType + "?extraFields=")).contains(expected);
        }

        @Test
        public void startSendingWithNamespacesFilterAndExtraFields() {
            final Collection<String> namespaces = Arrays.asList("eclipse", "ditto", "is", "awesome");
            final String filter = "eq(foo,1)";
            final ThingFieldSelector extraFields = ThingFieldSelector.fromJsonFieldSelector(
                    JsonFieldSelector.newInstance("attributes", "features/location"));
            final StartStreaming expected = StartStreaming.getBuilder(streamingType, correlationId, authContext)
                    .withNamespaces(namespaces)
                    .withFilter(filter)
                    .withExtraFields(extraFields)
                    .build();

            final String requestParams = MessageFormat.format("?filter={0}&namespaces={1}&extraFields={2}", filter,
                    String.join(",", namespaces), extraFields.toString());

            assertThat(underTest.apply(protocolMessageType + requestParams)).contains(expected);
        }

        @Test
        public void startSendingWithStrangeAppendix() {
            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext).build();

            assertThat(underTest.apply(protocolMessageType + "thisShouldNotBeBreakAnything")).contains(expected);
        }

        @Test
        public void startSendingWithCorrelationId() {

            final String msgCorrelationId = UUID.randomUUID().toString();

            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext)
                            .withCorrelationId(msgCorrelationId)
                            .build();

            assertThat(underTest.apply(protocolMessageType + "?" + String.format("%s=%s",
                    DittoHeaderDefinition.CORRELATION_ID.getKey(), msgCorrelationId))).contains(expected);
        }

        @Test
        public void startSendingWithUnknownParameters() {
            final StartStreaming expected =
                    StartStreaming.getBuilder(streamingType, correlationId, authContext).build();

            final Optional<StreamControlMessage> extracted = underTest.apply(protocolMessageType + "?eclipse=ditto");

            assertThat(extracted).contains(expected);
        }

    }

    @RunWith(Parameterized.class)
    public static final class StopSendingTest {

        @Parameterized.Parameter
        public ProtocolMessageType protocolMessageType;

        @Parameterized.Parameters(name = "{0}")
        public static List<ProtocolMessageType> startSendingProtocolMessageTypes() {
            return Arrays.stream(ProtocolMessageType.values())
                    .filter(protocolMessage -> protocolMessage.getIdentifier().startsWith("STOP"))
                    .collect(Collectors.toList());
        }

        @Test
        public void testStopSending() {
            final String correlationId = String.valueOf(UUID.randomUUID());
            final StreamControlMessage expected =
                    new StopStreaming(protocolMessageType.getStreamingTypeOrThrow(), correlationId);

            final ProtocolMessageExtractor underTest =
                    new ProtocolMessageExtractor(Mockito.mock(AuthorizationContext.class), correlationId);

            assertThat(underTest.apply(protocolMessageType.toString())).contains(expected);
        }

    }

}
