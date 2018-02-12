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


import java.util.List;

import org.eclipse.ditto.protocoladapter.Adaptable;

/**
 * Payload mapper interface. Defines functions for configuration an payload transformation.
 */
public interface PayloadMapper {

    /**
     * Get all content types supported by this mapper.
     *
     * @return all supported content types
     */
    List<String> getSupportedContentTypes();

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
     * @throws UnsupportedOperationException if this mapper does not support mapping of incoming messages
     */
    Adaptable mapIncoming(PayloadMapperMessage message);

    /**
     * Maps an outgoing message.
     *
     * @param dittoProtocolAdaptable the adaptible
     * @return the payload
     * @throws UnsupportedOperationException if this mapper does not support mapping of outgoing messages
     */
    PayloadMapperMessage mapOutgoing(Adaptable dittoProtocolAdaptable);
}
