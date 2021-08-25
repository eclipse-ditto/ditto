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

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;

final class TransformationResult {

    @Nullable private final DittoRuntimeException dittoRuntimeException;
    @Nullable private final ExternalMessage externalMessage;

    private TransformationResult(@Nullable final DittoRuntimeException dittoRuntimeException,
            @Nullable final ExternalMessage externalMessage) {
        this.dittoRuntimeException = dittoRuntimeException;
        this.externalMessage = externalMessage;
    }

    static TransformationResult successful(final ExternalMessage externalMessage) {
        return new TransformationResult(null, externalMessage);
    }

    static TransformationResult failed(final DittoRuntimeException dittoRuntimeException) {
        return new TransformationResult(dittoRuntimeException, null);
    }

    Optional<DittoRuntimeException> getDittoRuntimeException() {
        return Optional.ofNullable(dittoRuntimeException);
    }

    Optional<ExternalMessage> getExternalMessage() {
        return Optional.ofNullable(externalMessage);
    }

}
