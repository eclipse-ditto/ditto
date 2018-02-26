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

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.DefaultMessageMapperOptions;


public class MockMapper extends MessageMapper {

    public static final String OPT_IS_VALID = "Mock";

    public MockMapper() {
    }

    @Override
    public void doConfigure(@Nonnull final DefaultMessageMapperOptions configuration) {
        configuration.findProperty(OPT_IS_VALID).map(Boolean::valueOf).filter(Boolean.TRUE::equals).orElseThrow
                (IllegalArgumentException::new);
    }

    @Override
    protected Adaptable doForwardMap(@Nonnull final ExternalMessage externalMessage) {
        return null;
    }

    @Override
    protected ExternalMessage doBackwardMap(@Nonnull final Adaptable adaptable) {
        return null;
    }
}
