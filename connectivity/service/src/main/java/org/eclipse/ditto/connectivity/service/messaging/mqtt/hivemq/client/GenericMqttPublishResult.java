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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

/**
 * Result for a {@code GenericMqttPublish} sent by the client.
 */
public final class GenericMqttPublishResult {

    private final GenericMqttPublish genericMqttPublish;
    @Nullable private final Throwable error;

    private GenericMqttPublishResult(final GenericMqttPublish genericMqttPublish, @Nullable final Throwable error) {
        this.genericMqttPublish = ConditionChecker.checkNotNull(genericMqttPublish, "genericMqttPublish");
        this.error = error;
    }

    /**
     * Returns an instance of {@code GenericMqttPublishResult} that represents the successful delivery of a Publish
     * message.
     *
     * @param genericMqttPublish the successfully delivered Publish message.
     * @return the instance of the result.
     * @throws NullPointerException if {@code genericMqttPublish} is {@code null}.
     */
    public static GenericMqttPublishResult success(final GenericMqttPublish genericMqttPublish) {
        return new GenericMqttPublishResult(genericMqttPublish, null);
    }

    /**
     * Returns an instance of {@code GenericMqttPublishResult} that represents the failed delivery of a Publish
     * message.
     *
     * @param genericMqttPublish the Publish message that could not be delivered.
     * @param error the error that caused the delivery to fail.
     * @return the instance of the result.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static GenericMqttPublishResult failure(final GenericMqttPublish genericMqttPublish, final Throwable error) {
        return new GenericMqttPublishResult(genericMqttPublish, ConditionChecker.checkNotNull(error, "error"));
    }

    /**
     * Indicates whether this result represents the successful delivery of a Publish message.
     *
     * @return {@code true} if this result represents the successful delivery of a Publish message, {@code false} if
     * delivery failed.
     * @see #isFailure()
     */
    public boolean isSuccess() {
        return null == error;
    }

    /**
     * Indicates whether this result represents the failed delivery of a Publish message.
     *
     * @return {@code true} if this result represents the failed delivery of a Publish message, {@code false} if
     * delivery succeeded.
     * @see #isSuccess()
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the {@code GenericMqttPublish} this result is for.
     *
     * @return the Publish message this result is for.
     */
    public GenericMqttPublish getGenericMqttPublish() {
        return genericMqttPublish;
    }

    /**
     * Returns the optional error that is present if the Publish message was not successfully delivered.
     *
     * @return the optional error.
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Returns the error if this is result is a failure.
     * Throws an {@code IllegalStateException} else.
     *
     * @return the error.
     * @throws IllegalStateException if this result is a success.
     */
    public Throwable getErrorOrThrow() {
        if (isFailure()) {
            return error;
        } else {
            throw new IllegalStateException("Success cannot provide an error.");
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (GenericMqttPublishResult) o;
        return Objects.equals(genericMqttPublish, that.genericMqttPublish) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(genericMqttPublish, error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "genericMqttPublish=" + genericMqttPublish +
                ", error=" + error +
                "]";
    }

}
