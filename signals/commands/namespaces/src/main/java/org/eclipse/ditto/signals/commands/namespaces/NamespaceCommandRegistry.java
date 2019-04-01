/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.namespaces;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * A {@link org.eclipse.ditto.signals.base.JsonParsableRegistry} aware of all
 * {@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommand}s.
 */
@Immutable
public final class NamespaceCommandRegistry extends AbstractJsonParsableRegistry<NamespaceCommand> {

    private NamespaceCommandRegistry(final Map<String, JsonParsable<NamespaceCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns an instance of {@code NamespaceCommandRegistry}.
     *
     * @return the instance.
     */
    public static NamespaceCommandRegistry getInstance() {
        final Map<String, JsonParsable<NamespaceCommand>> parseStrategies = new HashMap<>();
        parseStrategies.put(BlockNamespace.TYPE, BlockNamespace::fromJson);
        parseStrategies.put(UnblockNamespace.TYPE, UnblockNamespace::fromJson);
        parseStrategies.put(PurgeNamespace.TYPE, PurgeNamespace::fromJson);

        return new NamespaceCommandRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(NamespaceCommand.JsonFields.TYPE);
    }

}
