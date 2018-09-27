/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.services.models.connectivity.placeholder.Placeholder;


/**
 * Simple placeholder that currently only supports {{ mqtt:topic }} as a placeholder. In the context of an incoming
 * MQTT message the placeholder is resolved with the message topic.
 */
class TopicPlaceholder implements Placeholder<String> {

    private static final String PREFIX = "mqtt";
    private static final String VALUE = "topic";

    static final TopicPlaceholder INSTANCE = new TopicPlaceholder();
    static final List<String> VALID_VALUES = Arrays.asList(PREFIX + Placeholder.SEPARATOR + VALUE);

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public boolean supports(final String name) {
        return VALUE.equalsIgnoreCase(name);
    }

    @Override
    public Optional<String> apply(final String input, final String name) {
        return supports(name) ? Optional.of(input) : Optional.empty();
    }

    private TopicPlaceholder() {
    }
}