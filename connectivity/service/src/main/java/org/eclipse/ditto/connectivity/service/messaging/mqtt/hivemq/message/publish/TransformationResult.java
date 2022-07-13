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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Represents the result of a MQTT message transformation.
 * It is exclusively either a success or a failure.
 *
 * @param <I> type of the transformation input.
 * @param <O> type of the successful transformation output.
 */
public abstract sealed class TransformationResult<I, O> permits TransformationFailure, TransformationSuccess {

    private final I transformationInput;

    /**
     * Constructs a new {@code TransformationResult}.
     */
    protected TransformationResult(final I transformationInput) {
        this.transformationInput = checkNotNull(transformationInput, "transformationInput");
    }

    /**
     * Indicates whether this result is a success.
     *
     * @return {@code true} if this result is a success, {@code false} else.
     */
    public abstract boolean isSuccess();

    /**
     * Indicates whether this result is a failure.
     *
     * @return {@code false} if this result is a failure, {@code true} else.
     * @see #isSuccess()
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns the output value if this result is a success.
     * Otherwise, an exception is thrown.
     *
     * @return the output value that represents the successful transformation.
     * @throws IllegalStateException if this result is a failure.
     * @see #isSuccess()
     */
    public abstract O getSuccessValueOrThrow();

    /**
     * Returns the {@code MqttPublishTransformationException} if this result is a failure.
     * Otherwise, an exception is thrown.
     *
     * @return the error that caused the transformation to fail.
     * @throws IllegalStateException if this result is a success.
     * @see #isFailure()
     */
    public abstract MqttPublishTransformationException getErrorOrThrow();

    /**
     * Returns the input value that was used to produce this {@code TransformationResult}.
     *
     * @return the input value of the transformation.
     */
    public I getTransformationInput() {
        return transformationInput;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (TransformationResult<?, ?>) o;
        return Objects.equals(transformationInput, that.transformationInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformationInput);
    }

}
