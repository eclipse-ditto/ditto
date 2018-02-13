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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

/**
 * TODO doc
 */
public interface PayloadMapperMessage {

    /**
     *
     * @return
     */
    Optional<String> getContentType();

    /**
     *
     * @return
     */
    Optional<ByteBuffer> getRawData();

    /**
     *
     * @return
     */
    Optional<String> getStringData();

    /**
     *
     * @return
     */
    Map<String, String> getHeaders();
}
