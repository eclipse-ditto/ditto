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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import kamon.context.Context;

/**
 * An empty noop implementation of {@code StartedStrace} interface.
 */
final class EmptyStartedTrace implements StartedTrace {

    private static final StartedTrace INSTANCE = new EmptyStartedTrace();

    private EmptyStartedTrace() {
    }

    /**
     * @return the {@code NoopStartedTrace}.
     */
    static StartedTrace getInstance() {
        return INSTANCE;
    }

    @Override
    public StartedTrace tag(final String key, final String value) {
        return this;
    }

    @Override
    public StartedTrace tags(final Map<String, String> tags) {
        return this;
    }

    @Override
    public void finish() {}

    @Override
    public void finishAfter(final Duration duration) {}

    @Override
    public StartedTrace fail(final String errorMessage) {
        return this;
    }

    @Override
    public StartedTrace fail(final String errorMessage, final Throwable throwable) {
        return this;
    }

    @Override
    public StartedTrace mark(final String key) {
        return this;
    }

    @Override
    public StartedTrace mark(final String key, final Instant at) {
        return this;
    }

    @Override
    public Context getContext() {
        return Context.Empty();
    }

    @Override
    public StartedTrace fail(final Throwable throwable) {
        return this;
    }

    @Override
    public Map<String, String> propagateContext(final Map<String, String> map) {
        return map;
    }
}
