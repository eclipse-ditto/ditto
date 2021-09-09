/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import akka.kafka.ConsumerMessage;

/**
 * Contains a {@link TransformationResult} containing either a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} or an
 * {@link org.eclipse.ditto.connectivity.api.ExternalMessage} and additionally a {@code committableOffset}
 */
@Immutable
final class CommittableTransformationResult {

    private final TransformationResult transformationResult;
    private final ConsumerMessage.CommittableOffset committableOffset;

    private CommittableTransformationResult(final TransformationResult transformationResult,
            final ConsumerMessage.CommittableOffset committableOffset) {
        this.transformationResult = transformationResult;
        this.committableOffset = committableOffset;
    }

    static CommittableTransformationResult of(final TransformationResult transformationResult,
            final ConsumerMessage.CommittableOffset committableOffset) {
        return new CommittableTransformationResult(transformationResult, committableOffset);
    }

    TransformationResult getTransformationResult() {
        return transformationResult;
    }

    ConsumerMessage.CommittableOffset getCommittableOffset() {
        return committableOffset;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CommittableTransformationResult that = (CommittableTransformationResult) o;
        return Objects.equals(transformationResult, that.transformationResult) &&
                Objects.equals(committableOffset, that.committableOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformationResult, committableOffset);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "transformationResult=" + transformationResult +
                ", committableOffset=" + committableOffset +
                "]";
    }

}
