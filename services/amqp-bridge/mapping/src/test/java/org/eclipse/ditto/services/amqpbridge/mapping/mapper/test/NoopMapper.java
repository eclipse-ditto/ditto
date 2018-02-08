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

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperMessage;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperOptions;

public class NoopMapper implements PayloadMapper {

    @Override
    public void configure(final PayloadMapperOptions options) {

    }

    @Override
    public Adaptable mapIncoming(final PayloadMapperMessage message) {
        return null;
    }

    @Override
    public PayloadMapperMessage mapOutgoing(final Adaptable dittoProtocolAdaptable) {
        return null;
    }
}
