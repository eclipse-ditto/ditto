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
import static org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory.newEnforcementFilterFactory;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;
import static org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaHeader.KAFKA_KEY;
import static org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaHeader.KAFKA_TIMESTAMP;
import static org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaHeader.KAFKA_TOPIC;
import static org.eclipse.ditto.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.EnforcementFilterFactory;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class KafkaMessageTransformerTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final long TIMESTAMP = System.currentTimeMillis();
    private KafkaMessageTransformer underTest;
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
        underTest = new KafkaMessageTransformer(ConnectionId.of("foo"),
                source, sourceAddress, enforcementFilterFactory, inboundMonitor);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(KafkaMessageTransformer.class, areImmutable(),
                provided(Source.class, EnforcementFilterFactory.class, ConnectionMonitor.class).areAlsoImmutable());
    }

    @Test
    public void messageIsTransformedToExternalMessage() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8))));
        final ConsumerRecord<String, ByteBuffer> consumerRecord = mock(ConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.key()).thenReturn("someKey");
        when(consumerRecord.value()).thenReturn(ByteBufferUtils.fromUtf8String("someValue"));
        when(consumerRecord.topic()).thenReturn("someTopic");
        when(consumerRecord.timestamp()).thenReturn(TIMESTAMP);
        final TransformationResult transformResult = underTest.transform(consumerRecord);

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.getExternalMessage()).isPresent();
        final ExternalMessage externalMessage = transformResult.getExternalMessage().get();
        assertThat(externalMessage.isTextMessage()).isTrue();
        assertThat(externalMessage.isBytesMessage()).isTrue();
        assertThat(externalMessage.getTextPayload()).contains("someValue");
        assertThat(externalMessage.getBytePayload()).contains(
                ByteBuffer.wrap("someValue".getBytes(StandardCharsets.UTF_8)));
        assertThat(externalMessage.getHeaders().get(KAFKA_TOPIC.getName())).isEqualTo("someTopic");
        assertThat(externalMessage.getHeaders().get(KAFKA_KEY.getName())).isEqualTo("someKey");
        assertThat(externalMessage.getHeaders().get(KAFKA_TIMESTAMP.getName())).isEqualTo(Long.toString(TIMESTAMP));
    }

    @Test
    public void messageWithBytepayloadIsTransformedToExternalMessage() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8))));
        final ByteBuffer bytePayload = ByteBuffer.wrap(new byte[]{0, 1, 2, 7, 5, 4});
        final ConsumerRecord<String, ByteBuffer> consumerRecord = mock(ConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.key()).thenReturn("someKey");
        when(consumerRecord.value()).thenReturn(bytePayload);
        when(consumerRecord.topic()).thenReturn("someTopic");
        when(consumerRecord.timestamp()).thenReturn(TIMESTAMP);
        final TransformationResult transformResult = underTest.transform(consumerRecord);

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.getExternalMessage()).isPresent();
        final ExternalMessage externalMessage = transformResult.getExternalMessage().get();
        assertThat(externalMessage.isTextMessage()).isTrue();
        assertThat(externalMessage.isBytesMessage()).isTrue();
        assertThat(externalMessage.getTextPayload()).contains(ByteBufferUtils.toUtf8String(bytePayload));
        assertThat(externalMessage.getBytePayload()).contains(bytePayload);
        assertThat(externalMessage.getHeaders().get(KAFKA_TOPIC.getName())).isEqualTo("someTopic");
        assertThat(externalMessage.getHeaders().get(KAFKA_KEY.getName())).isEqualTo("someKey");
        assertThat(externalMessage.getHeaders().get(KAFKA_TIMESTAMP.getName())).isEqualTo(Long.toString(TIMESTAMP));
    }

    @Test
    public void messageWithUTF16CharsetTransformedToExternalMessage() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8)),
                        new RecordHeader("content-type",
                                "text/plain; charset=utf-16".getBytes(StandardCharsets.UTF_8))));
        final String original = "Test";
        final byte[] utf16Bytes = original.getBytes(StandardCharsets.UTF_16);
        final ByteBuffer bytePayload = ByteBuffer.wrap(utf16Bytes);
        final ConsumerRecord<String, ByteBuffer> consumerRecord = mock(ConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.key()).thenReturn("someKey");
        when(consumerRecord.value()).thenReturn(bytePayload);
        when(consumerRecord.topic()).thenReturn("someTopic");
        when(consumerRecord.timestamp()).thenReturn(TIMESTAMP);
        final TransformationResult transformResult = underTest.transform(consumerRecord);

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.getExternalMessage()).isPresent();
        final ExternalMessage externalMessage = transformResult.getExternalMessage().get();
        assertThat(externalMessage.isTextMessage()).isTrue();
        assertThat(externalMessage.isBytesMessage()).isTrue();
        assertThat(externalMessage.getTextPayload()).contains("Test");
        assertThat(externalMessage.getBytePayload()).contains(bytePayload);
        assertThat(externalMessage.getHeaders().get(KAFKA_TOPIC.getName())).isEqualTo("someTopic");
        assertThat(externalMessage.getHeaders().get(KAFKA_KEY.getName())).isEqualTo("someKey");
        assertThat(externalMessage.getHeaders().get(KAFKA_TIMESTAMP.getName())).isEqualTo(Long.toString(TIMESTAMP));
    }

    @Test
    public void transformWithoutDeviceIdHeaderCausesDittoRuntimeException() {
        final RecordHeaders headers = new RecordHeaders(List.of());
        final ConsumerRecord<String, ByteBuffer> consumerRecord = mock(ConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.key()).thenReturn("someKey");
        when(consumerRecord.value()).thenReturn(ByteBufferUtils.fromUtf8String("someValue"));
        final TransformationResult transformResult = underTest.transform(consumerRecord);

        assertThat(transformResult).isNotNull();
        assertThat(transformResult.getDittoRuntimeException()).isPresent();
        assertThat(transformResult.getDittoRuntimeException().get()).isInstanceOf(UnresolvedPlaceholderException.class);
        final UnresolvedPlaceholderException error =
                (UnresolvedPlaceholderException) transformResult.getDittoRuntimeException().get();
        assertThat(error.getMessage()).contains("{{ header:device_id }}");
    }

    @Test
    public void unexpectedExceptionCausesMessageToBeDropped() {
        final String deviceId = "ditto:test-device";
        final RecordHeaders headers =
                new RecordHeaders(List.of(new RecordHeader("device_id", deviceId.getBytes(StandardCharsets.UTF_8))));
        final ConsumerRecord<String, ByteBuffer> consumerRecord = mock(ConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.key()).thenReturn("someKey");
        when(consumerRecord.value()).thenReturn(ByteBufferUtils.fromUtf8String("someValue"));
        doThrow(new IllegalStateException("Expected")).when(inboundMonitor).success(any(ExternalMessage.class));

        final TransformationResult transformResult = underTest.transform(consumerRecord);

        assertThat(transformResult).isNull();
    }

}
