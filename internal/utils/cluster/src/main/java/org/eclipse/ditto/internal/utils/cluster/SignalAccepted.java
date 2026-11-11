/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
 * Reply of an entity's supervisor to a signal carrying the {@code ditto-reply-signal-accepted} header once the signal
 * passed enforcement and the persistence actor acknowledged it, or it was forwarded to its target (e.g. live signals).
 */
@Immutable
public record SignalAccepted() implements PekkoJacksonCborSerializable {}
