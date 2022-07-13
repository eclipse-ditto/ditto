/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * A name and value UTF-8 encoded String pair.
 * This resembles a MQTT User Property according to the MQTT 5 specification.
 */
@Immutable
public record UserProperty(String name, String value) {

    /**
     * Constructs a {@code UserProperty} for the specified name and value arguments.
     *
     * @param name the name of the User Property.
     * @param value the value of the User Property.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty or blank.
     */
    public UserProperty(final String name, final String value) {
        this.name = assertNeitherNullNorBlank(name, "name");
        this.value = assertNeitherNullNorBlank(value, "value");
    }

    private static String assertNeitherNullNorBlank(final String s, final String argumentName) {
        return ConditionChecker.checkArgument(ConditionChecker.checkNotNull(s, argumentName),
                a -> !a.isBlank(),
                () -> MessageFormat.format("The {0} must not be blank.", argumentName));
    }

}
