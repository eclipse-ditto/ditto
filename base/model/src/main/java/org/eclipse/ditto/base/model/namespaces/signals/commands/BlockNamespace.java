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
package org.eclipse.ditto.base.model.namespaces.signals.commands;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;

/**
 * Command for blocking signals to a particular namespace.
 */
@Immutable
@JsonParsableCommand(typePrefix = AbstractNamespaceCommand.TYPE_PREFIX, name = BlockNamespace.NAME)
public final class BlockNamespace extends AbstractNamespaceCommand<BlockNamespace> {

    /**
     * The name of the {@code BlockNamespace} command.
     */
    static final String NAME = "blockNamespace";

    /**
     * The type of the {@code BlockNamespace} command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private BlockNamespace(final CharSequence namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    /**
     * Returns an instance of {@code BlockNamespace}.
     *
     * @param namespace the namespace to be blocked.
     * @param dittoHeaders the headers of the command.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    public static BlockNamespace of(final CharSequence namespace, final DittoHeaders dittoHeaders) {
        return new BlockNamespace(namespace, dittoHeaders);
    }

    /**
     * Creates a new {@code BlockNamespace} from a JSON object.
     *
     * @param jsonObject the JSON object of which the BlockNamespace is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link NamespaceCommand.JsonFields#NAMESPACE}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static BlockNamespace fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<BlockNamespace>(TYPE, jsonObject).deserialize(() -> {
            final String namespace = jsonObject.getValueOrThrow(NamespaceCommand.JsonFields.NAMESPACE);
            return new BlockNamespace(namespace, dittoHeaders);
        });
    }

    @Override
    public BlockNamespace setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new BlockNamespace(getNamespace(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof BlockNamespace;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
