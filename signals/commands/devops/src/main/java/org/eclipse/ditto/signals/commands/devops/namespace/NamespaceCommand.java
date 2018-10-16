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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.devops.DevOpsCommand;

/**
 * Supertype of namespace commands with convenient implementations of equals, hashcode and other inherited obligations.
 *
 * @param <T> Concrete types of each namespace command.
 */
public abstract class NamespaceCommand<T extends NamespaceCommand>
        extends AbstractCommand<T>
        implements DevOpsCommand<T> {

    private final String namespace;

    /**
     * Constructs a new {@code NamespaceCommand} object.
     *
     * @param namespace the namespace.
     * @param type the name of this command.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected NamespaceCommand(final String namespace, final String type, final DittoHeaders dittoHeaders) {
        super(type, dittoHeaders);
        this.namespace = checkNotNull(namespace, "namespace");
    }

    /**
     * Get the namespace at which the command is directed.
     *
     * @return the target namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(JsonFields.NAMESPACE, getNamespace());
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return DittoHeaders.empty();
    }

    @Override
    public Optional<String> getServiceName() {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> getInstance() {
        return Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        final Class<? extends NamespaceCommand> clazz = getClass();
        return clazz.isInstance(o) && Objects.equals(namespace, clazz.cast(o).getNamespace()) && super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespace);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", namespace=" + namespace + "]";
    }

    static <T extends NamespaceCommand> T fromJson(
            final BiFunction<String, DittoHeaders, T> constructor,
            final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return constructor.apply(jsonObject.getValueOrThrow(JsonFields.NAMESPACE), dittoHeaders);
    }

    /**
     * Collection of JSON fields.
     */
    public static final class JsonFields {

        /**
         * Namespace at which the command is directed.
         */
        public static final JsonFieldDefinition<String> NAMESPACE =
                JsonFactory.newStringFieldDefinition("namespace");
    }
}
