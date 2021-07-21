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

import org.eclipse.ditto.base.api.common.ModifyConfig;
import org.eclipse.ditto.base.api.common.ModifyConfigResponse;
import org.eclipse.ditto.json.JsonObject;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;

/**
 * Behavior to modify this actor's config.
 */
public interface ModifyConfigBehavior extends Actor {

    /**
     * Update config for this actor. It is up to implementations whether to include the current config as fallback.
     *
     * @param config the new config.
     * @return this actor's config after the modification.
     */
    Config setConfig(final Config config);

    /**
     * Injectable behavior to handle {@code ModifyConfig}.
     *
     * @return behavior to handle {@code ModifyConfig}.
     */
    default AbstractActor.Receive modifyConfigBehavior() {
        return ReceiveBuilder.create()
                .match(ModifyConfig.class, cmd -> {
                    final var newConfig = setConfig(ConfigFactory.parseString(cmd.getConfig().toString()));
                    final var newConfigJson =
                            JsonObject.of(newConfig.root().render(ConfigRenderOptions.concise()));
                    final var response =
                            ModifyConfigResponse.of(newConfigJson, cmd.getDittoHeaders());
                    sender().tell(response, self());
                })
                .build();
    }
}
