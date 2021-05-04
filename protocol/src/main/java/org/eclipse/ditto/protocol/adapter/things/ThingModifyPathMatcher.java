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
package org.eclipse.ditto.protocol.adapter.things;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.protocol.PayloadPathMatcher;
import org.eclipse.ditto.protocol.UnknownPathException;
import org.eclipse.ditto.things.model.signals.commands.ThingResource;

/**
 * PayloadPathMatcher implementation for modify thing commands.
 * <p>
 * @since 2.0.0
 */
final class ThingModifyPathMatcher implements PayloadPathMatcher {

    private static final Map<ThingResource, String> RESOURCE_NAMES = ThingResourceNames.get();

    private static final ThingModifyPathMatcher INSTANCE = new ThingModifyPathMatcher();

    private ThingModifyPathMatcher() {
    }

    static ThingModifyPathMatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public String match(final JsonPointer path) {
        final ThingResource resource =
                ThingResource.from(path).orElseThrow(() -> UnknownPathException.newBuilder(path).build());
        return Optional.ofNullable(RESOURCE_NAMES.get(resource))
                .orElseThrow(() -> UnknownPathException.newBuilder(path).build());
    }
}
