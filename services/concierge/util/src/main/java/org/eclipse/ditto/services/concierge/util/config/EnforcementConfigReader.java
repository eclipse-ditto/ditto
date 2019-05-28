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
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for enforcement settings.
 */
@Immutable
public final class EnforcementConfigReader extends AbstractConfigReader {

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_BUFFER_SIZE = 1_000;
    private static final int DEFAULT_PARALLELISM = 100;

    EnforcementConfigReader(final Config config) {
        super(config);
    }

    /**
     * Retrieve the ask timeout duration: the duration to wait for entity shard regions.
     *
     * @return the ask timeout duration.
     */
    public Duration askTimeout() {
        return getIfPresent("ask-timeout", config::getDuration).orElse(DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Retrieve the buffer size used for the queue in the {@code EnforcerActor}.
     *
     * @return the buffer size.
     */
    public int bufferSize() {
        return getIfPresent("buffer-size", config::getInt).orElse(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Retrieve the parallelism used for processing messages in parallel in {@code EnforcerActor}.
     *
     * @return the parallelism.
     */
    public int parallelism() {
        return getIfPresent("parallelism", config::getInt).orElse(DEFAULT_PARALLELISM);
    }

}
