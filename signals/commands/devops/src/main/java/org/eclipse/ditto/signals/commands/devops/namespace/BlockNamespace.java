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
 * Block namespace from messages.
 */
@Immutable
public final class BlockNamespace extends NamespaceCommand<BlockNamespace> {

    public static final String TYPE = TYPE_PREFIX + "blockNamespace";

    private BlockNamespace(final String namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    @Override
    public BlockNamespace setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new BlockNamespace(getNamespace(), dittoHeaders);
    }

    /**
     * Create a new block namespace command with empty headers.
     *
     * @param namespace the namespace to block.
     * @return the command.
     */
    public static BlockNamespace of(final String namespace) {
        return new BlockNamespace(namespace, DittoHeaders.empty());
    }

    /**
     * Deserialize from JSON.
     *
     * @param jsonObject serialized JSON.
     * @param dittoHeaders Ditto headers.
     * @return this command.
     */
    public static BlockNamespace fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return NamespaceCommand.fromJson(BlockNamespace::new, jsonObject, dittoHeaders);
    }
}
