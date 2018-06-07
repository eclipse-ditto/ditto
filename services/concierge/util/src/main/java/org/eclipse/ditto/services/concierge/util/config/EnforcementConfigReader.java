/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.util.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Configuration reader for enforcement settings.
 */
@Immutable
public final class EnforcementConfigReader extends AbstractConfigReader {

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(10);

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

}
