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
package org.eclipse.ditto.base.api.common;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;

/**
 * <p>
 * Abstract base implementation of common commands.
 * </p>
 * <p>
 * Allows to define a custom category.
 * </p>
 * <p>
 * <em>Note: Implementations of this class are required to be immutable.</em>
 * </p>
 *
 * @param <T> the type of the implementing class.
 */
@Immutable
public abstract class CommonCommand<T extends CommonCommand<T>> extends AbstractCommand<T> {

    /**
     * Type prefix of common commands.
     */
    protected static final String TYPE_PREFIX = "common." + TYPE_QUALIFIER + ":";

    /**
     * The resource type of common commands.
     */
    protected static final String RESOURCE_TYPE = "common";

    private final Category category;

    /**
     * Constructs a new {@code CommonCommand} object.
     *
     * @param type the name of this command.
     * @param category the category of this command.
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected CommonCommand(final String type, final Category category, final DittoHeaders dittoHeaders) {
        super(type, dittoHeaders);
        this.category = checkNotNull(category, "Category");
    }

    @Override
    public Category getCategory() {
        return category;
    }

    @Override
    public String getTypePrefix() {
        return TYPE_PREFIX;
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
        final CommonCommand<?> that = (CommonCommand<?>) o;
        return that.canEqual(this) && Objects.equals(category, that.category) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof CommonCommand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), category);
    }

    @Override
    public String toString() {
        return super.toString() + ", category=" + category;
    }

}
