/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import java.text.MessageFormat;

import javax.annotation.Nullable;

/**
 * An error class for reporting the fact that for a particular type a deserialization strategy could not be found.
 */
public final class DeserializationStrategyNotFoundError extends Error {

    private static final long serialVersionUID = 7508916584822620915L;

    /**
     * Constructs a new DeserializationStrategyNotFoundError object.
     *
     * @param entityClass the Class of the entity for which no deserialization strategy was found.
     */
    public DeserializationStrategyNotFoundError(final Class<?> entityClass) {
        this(entityClass, null);
    }

    /**
     * Constructs a new DeserializationStrategyNotFoundError object.
     *
     * @param entityClass the Class of the entity for which no deserialization strategy was found.
     * @param cause the cause of the error or {@code null} if no cause exists.
     */
    public DeserializationStrategyNotFoundError(final Class<?> entityClass, @Nullable final Throwable cause) {
        super(getMessage(entityClass.getName()), cause);
    }

    private static String getMessage(final String entityClassName) {
        return MessageFormat.format("Could not create deserialization strategy for <{0}>.", entityClassName);
    }

}
