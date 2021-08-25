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

import akka.kafka.ConsumerMessage;

final class CommittableTransformationResult {

    private final TransformationResult transformationResult;
    private final ConsumerMessage.CommittableOffset committableOffset;

    private CommittableTransformationResult(final TransformationResult transformationResult,
            final ConsumerMessage.CommittableOffset committableOffset) {
        this.transformationResult = transformationResult;
        this.committableOffset = committableOffset;
    }

    static CommittableTransformationResult of(final TransformationResult transformationResult, final
    ConsumerMessage.CommittableOffset committableOffset) {
        return new CommittableTransformationResult(transformationResult, committableOffset);
    }

    TransformationResult getTransformationResult() {
        return transformationResult;
    }

    ConsumerMessage.CommittableOffset getCommittableOffset() {
        return committableOffset;
    }

}
