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
 * Represents a failed transformation of a MQTT message.
 *
 * @param <I> type of the transformation input.
 * @param <O> type of the successful transformation output.
 */
public final class TransformationFailure<I, O> extends TransformationResult<I, O> {

    private final MqttPublishTransformationException error;

    private TransformationFailure(final I transformationInput, final MqttPublishTransformationException error) {
        super(transformationInput);
        this.error = error;
    }

    /**
     * Returns an instance of {@code TransformationFailure} with the specified argument.
     *
     * @param transformationInput the input value that could not be transformed.
     * @param error the error that caused the transformation to fail.
     * @param <I> type of the transformation input.
     * @param <O> type of the successful transformation output.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <I, O> TransformationFailure<I, O> of(final I transformationInput,
            final MqttPublishTransformationException error) {

        return new TransformationFailure<>(transformationInput, checkNotNull(error, "error"));
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public O getSuccessValueOrThrow() {
        throw new IllegalStateException("Failure cannot provide a success value.");
    }

    @Override
    public MqttPublishTransformationException getErrorOrThrow() {
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
        if (!super.equals(o)) {
            return false;
        }
        final var that = (TransformationFailure<?, ?>) o;
        return Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "transformationInput=" + getTransformationInput() +
                ", error=" + error +
                "]";
    }

}
