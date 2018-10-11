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
 * Ask if a namespace is empty.
 */
@Immutable
public final class QueryNamespaceEmptiness extends NamespaceCommand<QueryNamespaceEmptiness> {

    /**
     * Name of the command, or the part in the type without the type prefix.
     */
    public static final String NAME = "queryNamespaceEmptiness";

    /**
     * Type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private QueryNamespaceEmptiness(final String namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    @Override
    public QueryNamespaceEmptiness setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new QueryNamespaceEmptiness(getNamespace(), dittoHeaders);
    }

    /**
     * Create a new query namespace command with empty headers.
     *
     * @param namespace the namespace to query.
     * @return the command.
     */
    public static QueryNamespaceEmptiness of(final String namespace) {
        return new QueryNamespaceEmptiness(namespace, DittoHeaders.empty());
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject serialized JSON.
     * @param dittoHeaders Ditto headers.
     * @return this command.
     */
    public static QueryNamespaceEmptiness fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return NamespaceCommand.fromJson(QueryNamespaceEmptiness::new, jsonObject, dittoHeaders);
    }
}
