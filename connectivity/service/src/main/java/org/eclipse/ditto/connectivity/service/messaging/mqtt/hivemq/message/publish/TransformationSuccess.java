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
 * Represents the successful transformation of a MQTT message.
 *
 * @param <I> type of the transformation input.
 * @param <O> type of the successful transformation output.
 */
public final class TransformationSuccess<I, O> extends TransformationResult<I, O> {

    private final O transformationOutput;

    private TransformationSuccess(final I transformationInput, final O transformationOutput) {
        super(transformationInput);
        this.transformationOutput = transformationOutput;
    }

    /**
     * Returns an instance of {@code TransformationSuccess} with the specified argument.
     *
     * @param transformationInput the input value that was successfully transformed.
     * @param successValue the value that represents the successful transformation.
     * @param <I> type of the transformation input.
     * @param <O> type of the successful transformation output.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <I, O> TransformationSuccess<I, O> of(final I transformationInput, final O successValue) {
        return new TransformationSuccess<>(transformationInput, checkNotNull(successValue, "successValue"));
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public O getSuccessValueOrThrow() {
        return transformationOutput;
    }

    @Override
    public MqttPublishTransformationException getErrorOrThrow() {
        throw new IllegalStateException("Success cannot provide an error.");
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
        final var that = (TransformationSuccess<?, ?>) o;
        return Objects.equals(transformationOutput, that.transformationOutput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), transformationOutput);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "transformationInput=" + getTransformationInput() +
                ", transformationOutput=" + transformationOutput +
                "]";
    }

}
