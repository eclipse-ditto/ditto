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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.validation.ProtocolValidator;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public final class MqttValidator implements ProtocolValidator {

    private static final Collection<String> ACCEPTED_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("tcp", "ssl", "ws", "wss"));

    /**
     * Create a new {@code MqttConnectionSpec}.
     *
     * @return a new instance.
     */
    public static MqttValidator newInstance() {
        return new MqttValidator();
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.MQTT;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders) throws DittoRuntimeException {
        ProtocolValidator.validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, "MQTT 3.1.1");
        // TODO: check specificConfig of sources and targets
    }
}
