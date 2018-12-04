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

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.Command;

/**
 * Abstract base implementation of namespace commands.
 *
 * @param <T> Concrete types of each namespace command.
 */
@Immutable
abstract class AbstractNamespaceCommand<T extends AbstractNamespaceCommand> extends AbstractCommand<T>
        implements NamespaceCommand<T> {

    /**
     * Type prefix of namespace commands.
     */
    protected static final String TYPE_PREFIX = "namespaces." + TYPE_QUALIFIER + ":";

    /**
     * Namespace resource type.
     */
    static final String RESOURCE_TYPE = "namespaces";

    private final String namespace;

    /**
     * Constructs a new {@code AbstractNamespaceCommand} object.
     *
     * @param namespace the namespace.
     * @param type the type of the command.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code namespace} is empty.
     */
    protected AbstractNamespaceCommand(final CharSequence namespace, final String type,
            final DittoHeaders dittoHeaders) {

        super(type, dittoHeaders);
        this.namespace = argumentNotEmpty(namespace, "namespace").toString();
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
    }

    /**
     * Same as {@link #getNamespace()}.
     */
    @Override
    public String getId() {
        return getNamespace();
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonFactory.emptyPointer();
    }

    @Override
    public Command.Category getCategory() {
        return Command.Category.MODIFY;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AbstractNamespaceCommand<?> that = (AbstractNamespaceCommand<?>) o;
        return that.canEqual(this) && Objects.equals(namespace, that.namespace) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AbstractNamespaceCommand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), namespace);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(NamespaceCommand.JsonFields.NAMESPACE, getNamespace(), schemaVersion.and(predicate));
    }

    @Override
    public String toString() {
        return "namespace=" + namespace;
    }

}
