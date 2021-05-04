/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.security;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

/**
 * Provides configuration settings for the caches of the Gateway service.
 */
@Immutable
public interface CachesConfig {

    /**
     * Returns the configuration settings of the public key cache.
     *
     * @return the config.
     */
    CacheConfig getPublicKeysConfig();

}
