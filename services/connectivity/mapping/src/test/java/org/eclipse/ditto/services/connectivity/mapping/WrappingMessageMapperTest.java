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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
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

        when(mockMapper.map(any(ExternalMessage.class))).thenReturn(Optional.of(mockAdaptable));
        when(mockMapper.map(mockAdaptable)).thenReturn(Optional.of(mockMessage));
        when(mockAdaptable.getTopicPath()).thenReturn(ProtocolFactory.emptyTopicPath());
        when(mockAdaptable.getPayload()).thenReturn(ProtocolFactory.newPayload("{\"path\":\"/\"}"));

        underTest = WrappingMessageMapper.wrap(mockMapper);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void configure() {
        when(mockConfiguration.findContentType()).thenReturn(Optional.of("contentType"));

        underTest.configure(null, mockConfiguration);

        verify(mockMapper).configure(null, mockConfiguration);
    }

    @Test
    public void mapMessage() {

        final Adaptable actual = underTest.map(mockMessage).get();
        verify(mockMapper).map(any(ExternalMessage.class));
    }

    @Test
    public void mapAdaptable() {
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap(ExternalMessage.CONTENT_TYPE_HEADER, "contentType"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));

        final ExternalMessage actual = underTest.map(mockAdaptable).get();
        verify(mockAdaptable, VerificationModeFactory.atLeastOnce()).getHeaders();
        verify(mockMapper).map(mockAdaptable);
    }

}
