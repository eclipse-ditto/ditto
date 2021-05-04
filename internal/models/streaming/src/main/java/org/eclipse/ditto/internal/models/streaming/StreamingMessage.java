/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.streaming;

/**
 * Marker interface for streaming-related messages.
 */
@SuppressWarnings("squid:S1214")
public interface StreamingMessage {
    /**
     * Type Prefix of streaming messages - used to avoid overlaps with messages from other modules/services.
     */
    String TYPE_PREFIX = "streaming:";
}
