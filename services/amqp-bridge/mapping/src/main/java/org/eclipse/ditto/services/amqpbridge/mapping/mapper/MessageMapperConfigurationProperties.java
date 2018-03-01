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

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;

/**
 * Static configuration property keys for a {@link MessageMapperConfiguration}.
 */
public final class MessageMapperConfigurationProperties {

    private MessageMapperConfigurationProperties() {
        throw new AssertionError();
    }

    /**
     * TODO TJ doc
     */
    public static final String CONTENT_TYPE = ExternalMessage.CONTENT_TYPE_HEADER;
}
