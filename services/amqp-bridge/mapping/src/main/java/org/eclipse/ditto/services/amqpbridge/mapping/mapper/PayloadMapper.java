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


import org.eclipse.ditto.protocoladapter.Adaptable;

/**
 * Payload mapper interface. Defines functions for configuration an payload transformation.
 */
public interface PayloadMapper {

    /**
     * Configures the payload mapper with the given options.
     *
     * @param options the options
     */
    void configure(final PayloadMapperOptions options);

    /**
     * Maps an incomming payload.
     *
     * @param message the message
     * @return the adaptible
     */
    Adaptable mapIncoming(PayloadMapperMessage message);

    /**
     * Maps an outgoing message.
     *
     * @param dittoProtocolAdaptable the adaptible
     * @return the payload
     */
    PayloadMapperMessage mapOutgoing(Adaptable dittoProtocolAdaptable);
}
