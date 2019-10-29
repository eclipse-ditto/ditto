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
package org.eclipse.ditto.services.connectivity.mapping;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.internal.verification.VerificationModeFactory;

/**
 * Tests for {@link WrappingMessageMapper}.
 */
public class WrappingMessageMapperTest {

    private static final int limitOfIncommingMessages = 10;
    private static final int limitOfOutgoingMessages = 10;

    private MessageMapper mockMapper;
    private MessageMapper underTest;
    private MessageMapperConfiguration mockConfiguration;
    private ExternalMessage mockMessage;
    private Adaptable mockAdaptable;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() {
        mockMapper = mock(MessageMapper.class);
        mockConfiguration = mock(MessageMapperConfiguration.class);
        mockMessage = mock(ExternalMessage.class);
        mockAdaptable = mock(Adaptable.class);

        when(mockMapper.map(any(ExternalMessage.class))).thenReturn(singletonList(mockAdaptable));
        when(mockMapper.map(mockAdaptable)).thenReturn(singletonList(mockMessage));
        when(mockAdaptable.getTopicPath()).thenReturn(ProtocolFactory.emptyTopicPath());
        when(mockAdaptable.getPayload()).thenReturn(ProtocolFactory.newPayload("{\"path\":\"/\"}"));

        underTest = WrappingMessageMapper.wrap(mockMapper);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void configure() {
        when(mockConfiguration.getContentTypeBlacklist()).thenReturn(Collections.singletonList("blacklistedContentType"));

        underTest.configure(null, mockConfiguration);

        verify(mockMapper).configure(null, mockConfiguration);
    }

    @Test
    public void mapMessage() {
        underTest.map(mockMessage);
        verify(mockMapper).map(any(ExternalMessage.class));
    }

    @Test
    public void mapAdaptable() {
        final DittoHeaders headers = DittoHeaders.of(Collections.singletonMap(ExternalMessage.CONTENT_TYPE_HEADER, "contentType"));
        when(mockAdaptable.getHeaders()).thenReturn(Optional.of(headers));

        underTest.map(mockAdaptable);
        verify(mockAdaptable, VerificationModeFactory.atLeastOnce()).getHeaders();
        verify(mockMapper).map(mockAdaptable);
    }

    @Test
    public void mapMessageWithInvalidNumberOfMessages() {
        exception.expect(MessageMappingFailedException.class);
        List<Adaptable> listOfMockAdaptable = listWithInvalideNumberOfElements(mockAdaptable, limitOfIncommingMessages);
        when(mockMapper.map(any(ExternalMessage.class))).thenReturn(listOfMockAdaptable);

        underTest.map(mockMessage);
    }

    @Test
    public void mapAdaptableWithInvalidNumberOfMessages() {
        exception.expect(MessageMappingFailedException.class);
        List<ExternalMessage> listOfMockAdaptable =
                listWithInvalideNumberOfElements(mockMessage, limitOfOutgoingMessages);
        when(mockMapper.map(any(Adaptable.class))).thenReturn(listOfMockAdaptable);

        underTest.map(mockAdaptable);
    }

    private <T> List<T> listWithInvalideNumberOfElements(T elementInList, final int invalidLimitNumber) {
        List<T> listOfMockAdaptable = new ArrayList<>();
        for (int i = 0; i < invalidLimitNumber + 1; i++) {
            listOfMockAdaptable.add(elementInList);
        }
        return listOfMockAdaptable;
    }
}
