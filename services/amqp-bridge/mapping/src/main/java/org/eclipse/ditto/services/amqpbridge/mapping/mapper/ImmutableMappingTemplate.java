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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Objects;

/**
 * TODO doc
 */
public final class ImmutableMappingTemplate implements MappingTemplate {

    private final String mappingTemplate;

    /**
     *
     * @param mappingTemplate
     */
    public ImmutableMappingTemplate(final String mappingTemplate) {
        this.mappingTemplate = mappingTemplate;
    }

    @Override
    public String getMappingTemplate() {
        return mappingTemplate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutableMappingTemplate)) return false;
        final ImmutableMappingTemplate that = (ImmutableMappingTemplate) o;
        return Objects.equals(mappingTemplate, that.mappingTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappingTemplate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mappingTemplate=" + mappingTemplate +
                "]";
    }
}
