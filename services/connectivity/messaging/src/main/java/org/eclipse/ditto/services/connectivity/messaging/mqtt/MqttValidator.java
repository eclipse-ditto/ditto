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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.services.connectivity.messaging.validation.SpecificConfigValidator;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public final class MqttValidator extends AbstractProtocolValidator {

    private static final String QOS = "qos";

    private static final Collection<String> ACCEPTED_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("tcp", "ssl", "ws", "wss"));

    private static final Map<String, SpecificConfigValidator> SPECIFIC_CONFIG_VALIDATORS =
            Collections.singletonMap("qos", MqttValidator::validateQoS);

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
    public void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, "MQTT 3.1.1");
        validateSourceAndTargetConfigs(connection, dittoHeaders, SPECIFIC_CONFIG_VALIDATORS);
    }

    private static void validateQoS(final String qosString,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription) {

        boolean isError;
        try {
            // MQTT 3.1.1 quality of service can be 0, 1 or 2.
            final int qos = Integer.parseInt(qosString);
            isError = qos < 0 || qos > 2;
        } catch (final NumberFormatException e) {
            isError = true;
        }
        if (isError) {
            final String message = MessageFormat.format("Invalid value ''{0}'' for configuration ''{{1}}'' in {{2}}",
                    qosString, QOS, errorSiteDescription.get());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }
}
