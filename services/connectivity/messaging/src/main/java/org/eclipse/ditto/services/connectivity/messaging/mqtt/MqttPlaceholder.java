/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Optional;

import org.eclipse.ditto.services.models.connectivity.placeholder.Placeholder;

public class MqttPlaceholder implements Placeholder<String> {

    @Override
    public String getPrefix() {
        return "mqtt";
    }

    @Override
    public boolean supports(final String name) {
        return "topic" .equalsIgnoreCase(name);
    }

    @Override
    public Optional<String> apply(final String topic, final String name) {
        return supports(name) ? Optional.of(topic) : Optional.empty();
    }
}
