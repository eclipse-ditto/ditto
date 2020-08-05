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
package org.eclipse.ditto.services.utils.akka.actors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Mixing for creating actor names from a counter.
 */
public interface GenerateActorNamesFromCounter {

    /**
     * Generate a safe actor name from a counter and a suffix. If the counter increments every call then
     * the generated actor names are guaranteed to be unique.
     *
     * @param counter the counter.
     * @param suffix the suffix; may contain any character.
     * @return the generated actor name.
     */
    default String getActorNameFromCounter(final int counter, final String suffix) {
        return String.format("%x-%s", counter, URLEncoder.encode(suffix, StandardCharsets.UTF_8));
    }

}
