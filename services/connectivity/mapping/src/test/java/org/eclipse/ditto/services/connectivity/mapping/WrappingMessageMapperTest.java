/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.services.connectivity.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.verification.VerificationModeFactory;

/**
 * Tests for {@link WrappingMessageMapper}.
 */
public class WrappingMessageMapperTest {


    private MessageMapper mockMapper;
    private MessageMapper underTest;
    private MessageMapperConfiguration mockConfiguration;
    private ExternalMessage mockMessage;
    private Adaptable mockAdaptable;

    @Before
    public void setUp() {
        mockMapper = mock(MessageMapper.class);
        mockConfiguration = mock(MessageMapperConfiguration.class);
        mockMessage = mock(ExternalMessage.class);
        mockAdaptable = mock(Adaptable.class);

        when(mockMapper.map(mockMessage)).thenReturn(Optional.of(mockAdaptable));
        when(mockMapper.map(mockAdaptable)).thenReturn(Optional.of(mockMessage));
        when(mockAdaptable.getTopicPath()).thenReturn(ProtocolFactory.emptyTopicPath());
        when(mockAdaptable.getPayload()).thenReturn(ProtocolFactory.newPayload("{\"path\":\"/\"}"));

        underTest = WrappingMessageMapper.wrap(mockMapper);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void contentType() {
        underTest.getContentType();
        verify(mockMapper).getContentType();
        assertThat(underTest.getContentType()).isEqualTo(mockMapper.getContentType());
    }

    @Test
    public void configure() {
        when(mockConfiguration.findContentType()).thenReturn(Optional.of("contentType"));

        underTest.configureWithValidation(mockConfiguration);

        verify(mockMapper).configure(mockConfiguration);
    }

    @Test
    public void mapMessage() {
        when(mockMessage.findHeaderIgnoreCase(ExternalMessage.CONTENT_TYPE_HEADER)).thenReturn(Optional.of("contentType"));
        when(mockMapper.getContentType()).thenReturn("contentType");

        final Adaptable actual = underTest.map(mockMessage).get();
        verify(mockMapper).getContentType();
        verify(mockMessage, VerificationModeFactory.atLeastOnce()).findHeaderIgnoreCase(anyString());
        verify(mockMapper).map(mockMessage);
    }

    @Test
    public void mapAdaptable() {
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap(ExternalMessage.CONTENT_TYPE_HEADER, "contentType"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));
        when(mockMapper.getContentType()).thenReturn("contentType");

        final ExternalMessage actual = underTest.map(mockAdaptable).get();
        verify(mockMapper, VerificationModeFactory.atLeastOnce()).getContentType();
        verify(mockAdaptable, VerificationModeFactory.atLeastOnce()).getHeaders();
        verify(mockMapper).map(mockAdaptable);
    }

    @Test
    public void contentTypeOverride() {
        underTest = WrappingMessageMapper.wrap(mockMapper, "contentTypeOverride");
        underTest.getContentType();
        verify(mockMapper, never()).getContentType();
        assertThat(underTest.getContentType()).isEqualTo("contentTypeOverride");
    }

    @Test
    public void configureConfigurationWithoutContentTypeFails() {
        assertThatExceptionOfType(MessageMapperConfigurationInvalidException.class).isThrownBy(
                () -> underTest.configureWithValidation(mockConfiguration));
    }

    @Test
    public void mapMessageWithoutContentTypeHeaderFails() {
        when(mockMapper.getContentType()).thenReturn("contentType");

        assertThatExceptionOfType(MessageMappingFailedException.class).isThrownBy(
                () ->underTest.map(mockMessage));
    }

    @Test
    public void mapMessageWithoutDeviationgContentTypesFails() {
        when(mockMessage.findHeaderIgnoreCase(ExternalMessage.CONTENT_TYPE_HEADER)).thenReturn(Optional.of("a"));
        when(mockMapper.getContentType()).thenReturn("b");

        assertThatExceptionOfType(MessageMappingFailedException.class).isThrownBy(
                () ->underTest.map(mockMessage));
    }

    @Test
    public void mapAdaptableWithoutContentTypeHeaderFails() {
        when(mockMapper.getContentType()).thenReturn("contentType");

        assertThatExceptionOfType(MessageMappingFailedException.class).isThrownBy(
                () ->underTest.map(mockAdaptable));
    }

    @Test
    public void mapAdaptableWithoutDeviationgContentTypesFails() {
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap(ExternalMessage.CONTENT_TYPE_HEADER, "a"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));
        when(mockMapper.getContentType()).thenReturn("b");

        assertThatExceptionOfType(MessageMappingFailedException.class).isThrownBy(
                () ->underTest.map(mockAdaptable));
    }
}
