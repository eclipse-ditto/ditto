/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.streaming;

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
