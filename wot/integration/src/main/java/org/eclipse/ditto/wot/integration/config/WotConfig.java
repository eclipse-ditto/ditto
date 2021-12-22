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
package org.eclipse.ditto.wot.integration.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

/**
 * Provides configuration settings for WoT (Web of Things) integration.
 *
 * @since 2.4.0
 */
@Immutable
public interface WotConfig {

    /**
     * Returns configuration settings for the HTTP proxy to use for downloading WoT Thing Models.
     *
     * @return configuration settings for the HTTP proxy.
     */
    HttpProxyConfig getHttpProxyConfig();

    /**
     * Returns the cache configuration to apply for caching downloaded WoT Thing Models.
     *
     * @return the cache configuration to apply.
     */
    CacheConfig getCacheConfig();

    /**
     * Returns configuration settings for WoT (Web of Things) integration regarding the Thing Description transformation
     * from Thing Models.
     *
     * @return configuration regarding the Thing Description transformation from Thing Models.
     */
    ToThingDescriptionConfig getToThingDescriptionConfig();

    /**
     * Returns configuration for WoT TM (ThingModel) based creation of Things and Features.
     *
     * @return  configuration for WoT TM (ThingModel) based creation of Things and Features.
     */
    TmBasedCreationConfig getCreationConfig();

}
