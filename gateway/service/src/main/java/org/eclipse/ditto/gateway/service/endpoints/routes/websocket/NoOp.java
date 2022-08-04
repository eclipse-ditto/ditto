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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.gateway.service.streaming.signals.StreamControlMessage;

/**
 * This is a {@code StreamControlMessage} to explicitly express, that the WebSocket stream should remain as it is.
 */
@Immutable
final class NoOp implements StreamControlMessage {

    @Nullable private static NoOp instance;

    private NoOp() {
        super();
    }

    /**
     * Returns an instance of {@code NoOp}.
     *
     * @return the instance.
     */
    static NoOp getInstance() {
        var result = instance;
        if (null == result) {
            result = new NoOp();
            instance = result;
        }
        return result;
    }

}
