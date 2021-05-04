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
 * Command for unblocking signals to a particular namespace.
 */
@Immutable
@JsonParsableCommand(typePrefix = AbstractNamespaceCommand.TYPE_PREFIX, name = UnblockNamespace.NAME)
public final class UnblockNamespace extends AbstractNamespaceCommand<UnblockNamespace> {

    /**
     * The name of the {@code UnblockNamespace} command.
     */
    static final String NAME = "unblockNamespace";

    /**
     * The type of the {@code UnblockNamespace} command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private UnblockNamespace(final CharSequence namespace, final DittoHeaders dittoHeaders) {
        super(namespace, TYPE, dittoHeaders);
    }

    /**
     * Returns an instance of {@code UnblockNamespace}.
     *
     * @param namespace the namespace to be unblocked.
     * @param dittoHeaders the headers of the command.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    public static UnblockNamespace of(final CharSequence namespace, final DittoHeaders dittoHeaders) {
        return new UnblockNamespace(namespace, dittoHeaders);
    }

    /**
     * Creates a new {@code UnblockNamespace} from a JSON object.
     *
     * @param jsonObject the JSON object of which the UnblockNamespace is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain
     * {@link org.eclipse.ditto.base.model.namespaces.signals.commands.NamespaceCommand.JsonFields#NAMESPACE}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static UnblockNamespace fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<UnblockNamespace>(TYPE, jsonObject).deserialize(() -> {
            final String namespace = jsonObject.getValueOrThrow(NamespaceCommand.JsonFields.NAMESPACE);
            return new UnblockNamespace(namespace, dittoHeaders);
        });
    }

    @Override
    public UnblockNamespace setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new UnblockNamespace(getNamespace(), dittoHeaders);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof UnblockNamespace;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
