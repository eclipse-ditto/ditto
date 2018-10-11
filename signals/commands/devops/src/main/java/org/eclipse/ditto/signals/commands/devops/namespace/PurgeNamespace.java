/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.devops.namespace;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Purge namespace data.
 */
@Immutable
public final class PurgeNamespace extends NamespaceCommand<PurgeNamespace> {

    static final String NAME = "purgeNamespace";

    /**
     * Type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private PurgeNamespace(final String namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    @Override
    public PurgeNamespace setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new PurgeNamespace(getNamespace(), dittoHeaders);
    }

    /**
     * Create a new purge namespace command with empty headers.
     *
     * @param namespace the namespace to purge.
     * @return the command.
     */
    public static PurgeNamespace of(final String namespace) {
        return new PurgeNamespace(namespace, DittoHeaders.empty());
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject serialized JSON.
     * @param dittoHeaders Ditto headers.
     * @return this command.
     */
    public static PurgeNamespace fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return NamespaceCommand.fromJson(PurgeNamespace::new, jsonObject, dittoHeaders);
    }
}
