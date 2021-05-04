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
package org.eclipse.ditto.internal.utils.akka.actors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.api.common.RetrieveConfig;
import org.eclipse.ditto.base.api.common.RetrieveConfigResponse;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;

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
                    try {
                        final Config rootConfig = getConfig();
                        final ConfigValue configValue =
                                retrieveConfig.getPath().map(rootConfig::getValue).orElseGet(rootConfig::root);
                        final JsonValue configJson =
                                JsonFactory.readFrom(configValue.render(ConfigRenderOptions.concise()));
                        final RetrieveConfigResponse response =
                                RetrieveConfigResponse.of(configJson, retrieveConfig.getDittoHeaders());
                        sender().tell(response, self());
                    } catch (final ConfigException | DittoRuntimeException | JsonRuntimeException e) {
                        final JsonObject payload = JsonObject.newBuilder()
                                .set("error", e.toString())
                                .build();
                        final RetrieveConfigResponse response =
                                RetrieveConfigResponse.of(payload, retrieveConfig.getDittoHeaders());
                        sender().tell(response, self());
                    }
                })
                .build();
    }
}
