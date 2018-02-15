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

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapperConfiguration;

public class NoOpMapper extends MessageMapper {

    NoOpMapper() {
        super("contentType", true);
    }

    @Override
    public void configure(@Nonnull final MessageMapperConfiguration configuration) {

    }

    @Override
    protected Adaptable doForward(final InternalMessage message) {
        return null;
    }

    @Override
    protected InternalMessage doBackward(final Adaptable adaptable) {
        return null;
    }
}
