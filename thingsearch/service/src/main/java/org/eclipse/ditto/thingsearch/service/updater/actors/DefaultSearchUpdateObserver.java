/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default SearchUpdateObserver implementation.
 */
public class DefaultSearchUpdateObserver implements SearchUpdateObserver {

    public DefaultSearchUpdateObserver(final ActorSystem system, final Config config) {
        // nothing to do
    }

    @Override
    public void process(final Metadata metadata, @Nullable final JsonObject thingJson) {
        // noop
    }

}
