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
package org.eclipse.ditto.services.utils.akka.actors;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.commands.common.RetrieveConfig;
import org.eclipse.ditto.signals.commands.common.RetrieveConfigResponse;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Behavior to respond to {@code RetrieveConfig}.
 */
public interface RetrieveConfigBehavior extends Actor {

    /**
     * Get the config. Requires explicit setting because config relevant to this actor may not be
     * {@code context().system().settings().config()}.
     *
     * @return the config.
     */
    Config getConfig();

    /**
     * Injectable behavior to handle {@code RetrieveConfig}.
     *
     * @return behavior to handle {@code RetrieveConfig}.
     */
    default AbstractActor.Receive retrieveConfigBehavior() {
        return ReceiveBuilder.create()
                .match(RetrieveConfig.class, retrieveConfig -> {
                    final JsonObject configObject =
                            JsonObject.of(getConfig().root().render(ConfigRenderOptions.concise()));
                    final RetrieveConfigResponse response =
                            RetrieveConfigResponse.of(configObject, retrieveConfig.getDittoHeaders());
                    sender().tell(response, self());
                })
                .build();
    }
}
