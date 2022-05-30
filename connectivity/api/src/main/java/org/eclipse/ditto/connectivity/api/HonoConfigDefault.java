/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.api;
import com.typesafe.config.Config;
import akka.actor.ActorSystem;
public final class HonoConfigDefault implements HonoConfig {

    private final String baseUri;

    public HonoConfigDefault(final ActorSystem actorSystem) {
        final Config config = actorSystem.settings().config().getConfig(PREFIX);
        this.baseUri = config.getString(ConfigValues.BASE_URI.getConfigPath());
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }

}