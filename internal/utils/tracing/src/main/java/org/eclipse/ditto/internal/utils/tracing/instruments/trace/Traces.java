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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import kamon.context.Context;

/**
 * Factory methods for traces.
 */
public final class Traces {

    private Traces() {
        // No-Op because this is a factory class.
    }

    /**
     * Builds a {@link PreparedTrace} with the given name.
     */
    public static PreparedTrace newTrace(final Context context, final String name) {
        return new PreparedKamonTrace(context, name);
    }

    /**
     * Builds a {@link PreparedTrace} with the given name.
     */
    public static StartedTrace emptyTrace() {
        return EmptyStartedTrace.getInstance();
    }

}
