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
 * An exception signaling that a {@link org.eclipse.ditto.services.amqpbridge.mapping.mapper.MessageMapper} is not yet configured.
 */
public class NotYetConfiguredException extends MessageMapperException {

    private static final long serialVersionUID = 7361331553802393779L;

    public NotYetConfiguredException() {
        super();
    }

    public NotYetConfiguredException(final String message) {
        super(message);
    }

    public NotYetConfiguredException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public NotYetConfiguredException(final Throwable cause) {
        super(cause);
    }
}
