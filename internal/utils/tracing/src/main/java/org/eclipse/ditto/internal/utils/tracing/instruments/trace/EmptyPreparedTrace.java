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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * An empty noop implementation of {@code PreparedTrace} interface.
 */
final class EmptyPreparedTrace implements PreparedTrace {

    private static final PreparedTrace INSTANCE = new EmptyPreparedTrace();

    private EmptyPreparedTrace() {
    }

    /**
     * @return the {@code EmptyPreparedTrace} singleton instance.
     */
    static PreparedTrace getInstance() {
        return INSTANCE;
    }

    @Override
    public PreparedTrace tag(final String key, final String value) {
        return this;
    }

    @Override
    public PreparedTrace tags(final Map<String, String> tags) {
        return this;
    }

    @Nullable
    @Override
    public String getTag(final String key) {
        return null;
    }

    @Override
    public Map<String, String> getTags() {
        return Collections.emptyMap();
    }

    @Override
    public StartedTrace start() {
        return EmptyStartedTrace.getInstance();
    }

    @Override
    public StartedTrace startAt(final Instant startInstant) {
        return EmptyStartedTrace.getInstance();
    }

    @Override
    public <T> T run(final DittoHeaders dittoHeaders, final Function<DittoHeaders, T> function) {
        return function.apply(dittoHeaders);
    }

    @Override
    public void run(final DittoHeaders dittoHeaders, final Consumer<DittoHeaders> consumer) {
        consumer.accept(dittoHeaders);
    }
}
