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
package org.eclipse.ditto.signals.commands.namespaces;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command for asking if a namespace is empty.
 */
@Immutable
public final class QueryNamespaceEmptiness extends AbstractNamespaceCommand<QueryNamespaceEmptiness> {

    /**
     * Name of the command, or the part in the type without the type prefix.
     */
    public static final String NAME = "queryNamespaceEmptiness";

    /**
     * The type of the {@code QueryNamespaceEmptiness} command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private QueryNamespaceEmptiness(final CharSequence namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
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
     * Creates a new {@code QueryNamespaceEmptiness} from a JSON object.
     *
     * @param jsonObject the JSON object of which the QueryNamespaceEmptiness is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link org.eclipse.ditto.signals.commands.namespaces.NamespaceCommand.JsonFields#NAMESPACE}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static QueryNamespaceEmptiness fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<QueryNamespaceEmptiness>(TYPE, jsonObject).deserialize(() -> {
            final String namespace = jsonObject.getValueOrThrow(NamespaceCommand.JsonFields.NAMESPACE);
            return new QueryNamespaceEmptiness(namespace, dittoHeaders);
        });
    }

    @Override
    public QueryNamespaceEmptiness setDittoHeaders(final DittoHeaders dittoHeaders) {
        if (Objects.equals(getDittoHeaders(), dittoHeaders)) {
            return this;
        }
        return new QueryNamespaceEmptiness(getNamespace(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof QueryNamespaceEmptiness;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
