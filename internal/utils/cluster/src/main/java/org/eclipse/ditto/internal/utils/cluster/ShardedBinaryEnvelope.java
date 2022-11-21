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
package org.eclipse.ditto.internal.utils.cluster;

import javax.annotation.concurrent.Immutable;

/**
 * Envelope to hold a binary message to a sharded entity. Serialization is performed recursively.
 *
 * @param message Message to the sharded entity.
 * @param entityName Name of the recipient entity.
 * @since 3.1.0
 */
@Immutable
public record ShardedBinaryEnvelope(Object message, String entityName) {}
