/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;

/**
 * Immutable implementation of {@link SecurityDefinitions}.
 */
@Immutable
final class ImmutableSecurityDefinitions extends AbstractMap<String, SecurityScheme> implements SecurityDefinitions {

    private final Map<String, SecurityScheme> securityDefinitions;

    ImmutableSecurityDefinitions(final Map<String, SecurityScheme> securityDefinitions) {
        this.securityDefinitions = securityDefinitions;
    }

    @Override
    public Optional<SecurityScheme> getSecurityDefinition(final CharSequence securityDefinitionName) {
        return Optional.ofNullable(securityDefinitions.get(securityDefinitionName.toString()));
    }

    @Override
    public Set<Entry<String, SecurityScheme>> entrySet() {
        return securityDefinitions.entrySet();
    }

    @Override
    public JsonObject toJson() {
        return securityDefinitions.entrySet().stream()
                .map(e -> JsonField.newInstance(e.getKey(), e.getValue().toJson()))
                .collect(JsonCollectors.fieldsToObject());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSecurityDefinitions that = (ImmutableSecurityDefinitions) o;
        return Objects.equals(securityDefinitions, that.securityDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(securityDefinitions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "securityDefinitions=" + securityDefinitions +
                "]";
    }
}
