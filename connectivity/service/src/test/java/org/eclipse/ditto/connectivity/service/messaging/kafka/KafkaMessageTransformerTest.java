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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.api.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.internal.models.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.models.placeholders.UnresolvedPlaceholderException;
import org.junit.Before;
import org.junit.Test;

public final class KafkaMessageTransformerTest {

    private KafkaMessageTransformer underTest;
    private ProcessorContext context;
    private ConnectionMonitor inboundMonitor;

    @Before
    public void setup() {
        final String sourceAddress = "test";
        final Enforcement enforcement = ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                Collections.singleton("{{ thing:id }}"));
        final Source source = ConnectivityModelFactory.newSourceBuilder()
                .address(sourceAddress)
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .enforcement(enforcement)
                .qos(1)
                .build();
        final EnforcementFilterFactory<Map<String, String>, Signal<?>> enforcementFilterFactory =
                newEnforcementFilterFactory(enforcement, newHeadersPlaceholder());
        inboundMonitor = mock(ConnectionMonitor.class);
        underTest = new KafkaMessageTransformer(source, sourceAddress, enforcementFilterFactory, inboundMonitor);
        context = mock(ProcessorContext.class);
        underTest.init(context);
    }

    @Test
    public void messageIsTransformedToExternalMessage() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8))));
        when(context.headers()).thenReturn(headers);

        final KeyValue<String, Object> transformResult = underTest.transform("someKey", "someValue");

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.value).isInstanceOf(ExternalMessage.class);
        final ExternalMessage externalMessage = (ExternalMessage) transformResult.value;
        assertThat(externalMessage.isTextMessage()).isTrue();
        assertThat(externalMessage.isBytesMessage()).isTrue();
        assertThat(externalMessage.getTextPayload()).contains("someValue");
        assertThat(externalMessage.getBytePayload()).contains(
                ByteBuffer.wrap("someValue".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void transformWithoutDeviceIdHeaderCausesDittoRuntimeException() {
        final RecordHeaders headers = new RecordHeaders(List.of());
        when(context.headers()).thenReturn(headers);

        final KeyValue<String, Object> transformResult = underTest.transform("someKey", "someValue");

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.value).isInstanceOf(UnresolvedPlaceholderException.class);
        final UnresolvedPlaceholderException error = (UnresolvedPlaceholderException) transformResult.value;
        assertThat(error.getMessage()).contains("{{ header:device_id }}");
    }

    @Test
    public void unexpectedExceptionCausesMessageToBeDropped() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8))));
        doThrow(new IllegalStateException("Expected")).when(inboundMonitor).success(any(ExternalMessage.class));
        when(context.headers()).thenReturn(headers);

        final KeyValue<String, Object> transformResult = underTest.transform("someKey", "someValue");

        assertThat(transformResult).isNull();
    }

}
