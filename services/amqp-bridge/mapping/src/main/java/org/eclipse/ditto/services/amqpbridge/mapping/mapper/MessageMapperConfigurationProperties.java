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

/**
 * Static configuration property keys for a {@link MessageMapperConfiguration}.
 */
final class MessageMapperConfigurationProperties {

    private MessageMapperConfigurationProperties() {
        throw new AssertionError();
    }

    public static final String CONTENT_TYPE = "contentType";
}
