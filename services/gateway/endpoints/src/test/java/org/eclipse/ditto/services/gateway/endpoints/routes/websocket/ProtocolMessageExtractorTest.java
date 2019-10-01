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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.StopStreaming;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests {@link ProtocolMessageExtractor}.
 */
public class ProtocolMessageExtractorTest {

    private String correlationId;
    private AuthorizationContext authorizationContext;
    private ProtocolMessageExtractor extractor;

    @Before
    public void setUp() {
        authorizationContext = Mockito.mock(AuthorizationContext.class);
        correlationId = UUID.randomUUID().toString();
        extractor = new ProtocolMessageExtractor(authorizationContext, correlationId);
    }

    @Test
    public void startSending() {
        testStartSending("", Collections.emptyList(), null);
    }

    @Test
    public void startSendingWithNamespaces() {
        testStartSending("?namespaces=eclipse,ditto,is,awesome",
                asList("eclipse", "ditto","is", "awesome"), null);
    }
    @Test
    public void startSendingWithFilter() {
        testStartSending("?filter=eq(foo,1)", Collections.emptyList(), "eq(foo,1)");
    }

    @Test
    public void startSendingWithNamespacesAndFilter() {
        testStartSending("?filter=eq(foo,1)&namespaces=eclipse,ditto,is,awesome",
                asList("eclipse", "ditto","is", "awesome"), "eq(foo,1)");
    }

    @Test
    public void startSendingWithEmptyFilter() {
        testStartSending("?filter=", Collections.emptyList(), "");
    }
    @Test
    public void startSendingWithEmptyNamespace() {
        testStartSending("?namespaces=", Collections.emptyList(), null);
    }

    @Test
    public void startSendingWithStrangeAppendix() {
        testStartSending("thisShouldNotBeBreakAnything", Collections.emptyList(), null);
    }

    @Test
    public void startSendingWithUnknownParameters() {
        testStartSending("?eclipse=ditto", Collections.emptyList(), null);
    }

    private void testStartSending(final String parameters, final List<String> expectedNamespaces,
            @Nullable final String expectedFilter) {
        Stream.of(ProtocolMessages.values())
                .filter(protocolMessage -> protocolMessage.getIdentifier().startsWith("START"))
                .forEach(protocolMessage -> {
                    final Object extracted = extractor.apply(protocolMessage.getIdentifier() + parameters);
                    assertThat(extracted).isInstanceOfAny(StartStreaming.class);
                    final StartStreaming start = ((StartStreaming) extracted);
                    assertThat(start.getStreamingType()).isEqualTo(protocolMessage.getStreamingType().get());
                    assertThat(start.getConnectionCorrelationId()).isEqualTo(correlationId);
                    assertThat(start.getAuthorizationContext()).isEqualTo(authorizationContext);
                    assertThat(start.getNamespaces()).isEqualTo(expectedNamespaces);
                    assertThat(start.getFilter()).isEqualTo(Optional.ofNullable(expectedFilter));
                });
    }

    @Test
    public void testStopSending() {
        Stream.of(ProtocolMessages.values())
                .filter(protocolMessage -> protocolMessage.getIdentifier().startsWith("STOP"))
                .forEach(protocolMessage -> {
                    final Object extracted = extractor.apply(protocolMessage.getIdentifier());
                    assertThat(extracted).isInstanceOfAny(StopStreaming.class);
                    final StopStreaming stop = ((StopStreaming) extracted);
                    assertThat(stop.getStreamingType()).isEqualTo(protocolMessage.getStreamingType().get());
                    assertThat(stop.getConnectionCorrelationId()).isEqualTo(correlationId);
                });
    }

    @Test
    public void noneProtocolMessagesMappedToNull() {
        assertThat(extractor.apply(null)).isNull();
        assertThat(extractor.apply("")).isNull();
        assertThat(extractor.apply("{\"some\":\"json\"}")).isNull();
    }
}
