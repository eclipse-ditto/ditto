/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.commands.devops.namespace;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Shutdown all actors in a namespace.
 */
@Immutable
public final class ShutdownNamespace extends NamespaceCommand<ShutdownNamespace> {

    public static final String TYPE = TYPE_PREFIX + "shutdownNamespace";

    private ShutdownNamespace(final String namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    @Override
    public ShutdownNamespace setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ShutdownNamespace(getNamespace(), dittoHeaders);
    }

    /**
     * Create a new shutdown namespace command with empty headers.
     *
     * @param namespace the namespace to shutdown.
     * @return the command.
     */
    public static ShutdownNamespace of(final String namespace) {
        return new ShutdownNamespace(namespace, DittoHeaders.empty());
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject serialized JSON.
     * @param dittoHeaders Ditto headers.
     * @return this command.
     */
    public static ShutdownNamespace fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return NamespaceCommand.fromJson(ShutdownNamespace::new, jsonObject, dittoHeaders);
    }
}
