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

package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.model.amqpbridge.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.amqpbridge.MessageMappingFailedException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("NullableProblems")
public class ContentTypeRestrictedMessageMapperTest {


    private MessageMapper mockMapper;
    private MessageMapper underTest;
    private MessageMapperConfiguration mockConfiguration;
    private ExternalMessage mockMessage;
    private Adaptable mockAdaptable;

    @Before
    public void setUp() throws Exception {
        mockMapper = mock(MessageMapper.class);
        mockConfiguration = mock(MessageMapperConfiguration.class);
        mockMessage = mock(ExternalMessage.class);
        mockAdaptable = mock(Adaptable.class);

        when(mockMapper.map(mockMessage)).thenReturn(mockAdaptable);
        when(mockMapper.map(mockAdaptable)).thenReturn(mockMessage);

        underTest = ContentTypeRestrictedMessageMapper.wrap(mockMapper);
    }

    @After
    public void tearDown() throws Exception {
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
        when(mockMessage.findHeaderIgnoreCase("content-type")).thenReturn(Optional.of("contentType"));
        when(mockMapper.getContentType()).thenReturn("contentType");

        final Adaptable actual = underTest.map(mockMessage);
        verify(mockMapper).getContentType();
        verify(mockMessage).findHeaderIgnoreCase(anyString());
        verify(mockMapper).map(mockMessage);
        assertThat(actual).isEqualTo(mockAdaptable);
    }

    @Test
    public void mapAdaptable() {
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap("content-type", "contentType"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));
        when(mockMapper.getContentType()).thenReturn("contentType");

        final ExternalMessage actual = underTest.map(mockAdaptable);
        verify(mockMapper).getContentType();
        verify(mockAdaptable).getHeaders();
        verify(mockMapper).map(mockAdaptable);
        assertThat(actual).isEqualTo(mockMessage);
    }

    @Test
    public void contentTypeOverride() {
        underTest = ContentTypeRestrictedMessageMapper.wrap(mockMapper, "contentTypeOverride");
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
        when(mockMessage.findHeaderIgnoreCase("content-type")).thenReturn(Optional.of("a"));
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
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap("content-type", "a"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));
        when(mockMapper.getContentType()).thenReturn("b");

        assertThatExceptionOfType(MessageMappingFailedException.class).isThrownBy(
                () ->underTest.map(mockAdaptable));
    }
}
