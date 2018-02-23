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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class MessageMapperTest {

    private MessageMapper mapper;

    abstract protected MessageMapper createMapper();

    abstract protected String createSupportedContentType();

    abstract protected List<MessageMapperConfiguration> createValidConfig();

    abstract protected Map<MessageMapperConfiguration, Throwable> createInvalidConfig();

    abstract protected MessageMapperConfiguration createIncomingConfig();

    abstract protected Map<InternalMessage, Adaptable> createValidIncomingMappings();

    abstract protected Map<InternalMessage, Throwable> createInvalidIncomingMappings();

    abstract protected MessageMapperConfiguration createOutgoingConfig();

    abstract protected Map<Adaptable, InternalMessage> createValidOutgoingMappings();

    abstract protected Map<Adaptable, Throwable> createInvalidOutgoingMappings();

    @Before
    public void setUp() throws Exception {
        this.mapper = createMapper();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void configure() throws Exception {
        createValidConfig().forEach(mapper::configure);
    }

    @Test
    public void supportedContentTypes() throws Exception {
        assertThat(mapper.getContentType()).isEqualTo(createSupportedContentType());
    }

    @Test
    public void configureNullOptionsFails() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> mapper.configure(null));
    }

    @Test
    public void configureFails() throws Exception {
        createInvalidConfig().forEach((opt, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.configure(opt))
                        .withMessage(err.getMessage())
                        .withCause(err.getCause()));
    }

    @Test
    public void mapIncoming() throws Exception {
        mapper.configure(createIncomingConfig());
        createValidIncomingMappings().forEach((m, a) -> assertThat(mapper.convert(m)).isEqualTo(a));
    }

    @Test
    public void mapIncomingFails() throws Exception {
        mapper.configure(createIncomingConfig());
        createInvalidIncomingMappings().forEach((m, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.convert(m))
                        .withMessageContaining(err.getMessage())
                        .withCause(err.getCause()));
    }

    @Test
    public void mapOutgoing() throws Exception {
        mapper.configure(createOutgoingConfig());
        createValidOutgoingMappings().forEach((a, m) -> assertThat(mapper.reverse().convert(a)).isEqualTo(m));
    }

    @Test
    public void mapOutgoingFails() throws Exception {
        mapper.configure(createOutgoingConfig());
        createInvalidOutgoingMappings().forEach((a, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.reverse().convert(a))
                        .withMessage(err.getMessage())
                        .withCause(err.getCause()));
    }


}
