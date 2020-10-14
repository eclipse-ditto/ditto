/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

/*
 * Copyright Bosch.IO GmbH 2020
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch.IO GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch.IO GmbH.
 */
package org.eclipse.ditto.services.connectivity.config;

import java.util.concurrent.CompletionStage;

import org.atteo.classindex.IndexSubclasses;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;

import akka.actor.ActorRef;

/**
 * Provides methods to load {@link ConnectivityConfig} and register for changes to {@link ConnectivityConfig}.
 */
@IndexSubclasses
public interface ConnectivityConfigProvider {

    /**
     * Loads a {@link ConnectivityConfig} by a connection ID.
     *
     * @param connectionId the connection id for which to load the {@link ConnectivityConfig}
     * @return the connectivity config
     */
    ConnectivityConfig getConnectivityConfig(ConnectionId connectionId);

    /**
     * Asynchronously loads a {@link ConnectivityConfig} by a connection ID.
     *
     * @param connectionId the connection id for which to load the {@link ConnectivityConfig}
     * @return the connectivity config
     */
    CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(ConnectionId connectionId);

    /**
     * Loads a {@link ConnectivityConfig} by some ditto headers.
     *
     * @param dittoHeaders the ditto headers for which to load the {@link ConnectivityConfig}
     * @return the connectivity config
     */
    ConnectivityConfig getConnectivityConfig(DittoHeaders dittoHeaders);

    /**
     * Asynchronously loads a {@link ConnectivityConfig} by some ditto headers.
     *
     * @param dittoHeaders the ditto headers for which to load the {@link ConnectivityConfig}
     * @return the connectivity config
     */
    CompletionStage<ConnectivityConfig> getConnectivityConfigAsync(DittoHeaders dittoHeaders);

    /**
     * Register the given {@code subscriber} for changes to the {@link ConnectivityConfig} of the given {@code
     * connectionId}. The given {@link ActorRef} will receive {@link ConnectivityConfigBuildable} to build the final
     * {@link ConnectivityConfig} given a default/fallback {@link ConnectivityConfig}.
     *
     * @param connectionId the connection id
     * @param subscriber the subscriber that will receive {@link ConnectivityConfigBuildable} messages
     */
    void registerForConnectivityConfigChanges(final ConnectionId connectionId, final ActorRef subscriber);
}
