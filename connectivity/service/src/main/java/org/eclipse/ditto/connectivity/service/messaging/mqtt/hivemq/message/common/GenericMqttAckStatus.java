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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.common;

import java.text.MessageFormat;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

/**
 * Generic representation of an MQTT Ack message status to abstract HiveMQ API for protocol versions 3 and 5.
 */
@Immutable
public abstract class GenericMqttAckStatus {

    private final int code;
    private final String name;
    private final boolean error;

    /**
     * Constructs a {@code GenericMqttAckStatus} for the specified arguments.
     *
     * @param code the code of the MQTT Ack message status.
     * @param name the name of the MQTT Ack message status.
     * @param error indicates whether the MQTT Ack message status is an error.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty or blank.
     */
    protected GenericMqttAckStatus(final int code, final String name, final boolean error) {
        this.code = code;
        this.name = ConditionChecker.checkArgument(ConditionChecker.checkNotNull(name, "name"),
                arg -> !arg.isBlank(),
                () -> "The name must not be blank.");
        this.error = error;
    }

    /**
     * Returns the code of this MQTT Ack message status.
     *
     * @return the byte code of this MQTT Ack message status.
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the name of this MQTT Ack message status.
     *
     * @return the name of this MQTT Ack message status.
     */
    public String getName() {
        return name;
    }

    /**
     * Indicates whether this status of a MQTT Ack message is an error.
     *
     * @return {@code true} if this MQTT Ack message status represents an error, {@code false} else.
     */
    public boolean isError() {
        return error;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (GenericMqttAckStatus) o;
        return code == that.code && error == that.error && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name, error);
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0}: {1}({2,number,integer})",
                isError() ? "Error" : "Success",
                getName(),
                getCode());
    }

}
