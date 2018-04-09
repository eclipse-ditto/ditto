/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * The default implementation of a {@link MessageMapperRegistry}.
 */
public final class DefaultMessageMapperRegistry implements MessageMapperRegistry {

    private final MessageMapper defaultMapper;
    @Nullable private final MessageMapper mapper;

    private DefaultMessageMapperRegistry(final MessageMapper defaultMapper, @Nullable final MessageMapper mapper) {
        this.defaultMapper = checkNotNull(defaultMapper);
        this.mapper = mapper;
    }

    @Override
    public MessageMapper getDefaultMapper() {
        return defaultMapper;
    }

    @Override
    public Optional<MessageMapper> getMapper() {
        return Optional.ofNullable(mapper);
    }

    /**
     * Creates a new registry of the parameter values.
     *
     * @param defaultMapper the default mapper
     * @param mapper the mapper
     */
    public static DefaultMessageMapperRegistry of(final MessageMapper defaultMapper, @Nullable final MessageMapper mapper) {
        return new DefaultMessageMapperRegistry(defaultMapper, mapper);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMessageMapperRegistry that = (DefaultMessageMapperRegistry) o;
        return Objects.equals(mapper, that.mapper) &&
                Objects.equals(defaultMapper, that.defaultMapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapper, defaultMapper);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "defaultMapper=" + defaultMapper +
                ", mapper=" + mapper +
                "]";
    }
}
