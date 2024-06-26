/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.http.config;

import javax.annotation.concurrent.Immutable;

import org.apache.pekko.http.javadsl.ClientTransport;
import org.eclipse.ditto.internal.utils.config.http.HttpProxyBaseConfig;

/**
 * Provides configuration settings for the HTTP proxy with additional Pekko HTTP specifics.
 */
@Immutable
public interface HttpProxyConfig extends HttpProxyBaseConfig {

    /**
     * Converts the proxy settings to a Pekko HTTP client transport object.
     * Does not check whether the proxy is enabled.
     *
     * @return a Pekko HTTP client transport object matching this config.
     */
    ClientTransport toClientTransport();

}
