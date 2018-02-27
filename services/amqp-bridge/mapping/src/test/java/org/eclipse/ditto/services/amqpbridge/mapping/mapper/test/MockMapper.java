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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.test;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;


public class MockMapper implements MessageMapper {

    public static final String OPT_IS_VALID = "Mock";

    @Nullable
    private String contentType;

    public MockMapper() {
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public void configure(final MessageMapperConfiguration configuration) {
        configuration.findContentType().ifPresent(s -> contentType = s);
        configuration.findProperty(OPT_IS_VALID).map(Boolean::valueOf).filter(Boolean.TRUE::equals).orElseThrow
                (IllegalArgumentException::new);
    }

    @Override
    public Adaptable map(final ExternalMessage message) {
        return null;
    }

    @Override
    public ExternalMessage map(final Adaptable adaptable) {
        return null;
    }


}
