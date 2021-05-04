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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.connectivity.service.mapping.DefaultMessageMapperConfiguration;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperConfiguration;

/**
 * Immutable implementation of {@link JavaScriptMessageMapperConfiguration}.
 */
@Immutable
final class ImmutableJavaScriptMessageMapperConfiguration implements JavaScriptMessageMapperConfiguration {

    private final MessageMapperConfiguration delegationTarget;

    private ImmutableJavaScriptMessageMapperConfiguration(final MessageMapperConfiguration theDelegationTarget) {
        delegationTarget = theDelegationTarget;
    }

    @Override
    public String getId() {
        return delegationTarget.getId();
    }

    @Override
    public Map<String, JsonValue> getProperties() {
        return delegationTarget.getProperties();
    }

    @Override
    public Map<String, String> getIncomingConditions() {
        return delegationTarget.getIncomingConditions();
    }

    @Override
    public Map<String, String> getOutgoingConditions() {
        return delegationTarget.getOutgoingConditions();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJavaScriptMessageMapperConfiguration that = (ImmutableJavaScriptMessageMapperConfiguration) o;
        return Objects.equals(delegationTarget, that.delegationTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegationTarget);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "properties=" + getProperties() +
                ", incomingConditions=" + getIncomingConditions() +
                ", outgoingConditions=" + getOutgoingConditions() +
                "]";
    }

    /**
     * Mutable Builder for {@link JavaScriptMessageMapperConfiguration}.
     */
    @NotThreadSafe
    static final class Builder implements JavaScriptMessageMapperConfiguration.Builder {

        private final String id;
        private final Map<String, JsonValue> properties;
        private final Map<String, String> incomingConditions;
        private final Map<String, String> outgoingConditions;

        Builder(final String id, final Map<String, JsonValue> properties, final Map<String, String> incomingConditions,
                final Map<String, String> outgoingConditions) {
            this.id = id;
            this.properties = new HashMap<>(properties); // mutable map!
            this.incomingConditions = incomingConditions;
            this.outgoingConditions = outgoingConditions;
        }

        @Override
        public Map<String, JsonValue> getProperties() {
            return properties;
        }

        @Override
        public Map<String, String> getIncomingConditions() {
            return incomingConditions;
        }

        @Override
        public Map<String, String> getOutgoingConditions() {
            return outgoingConditions;
        }

        @Override
        public JavaScriptMessageMapperConfiguration build() {
            return new ImmutableJavaScriptMessageMapperConfiguration(
                    DefaultMessageMapperConfiguration.of(id, properties, incomingConditions, outgoingConditions));
        }

    }

}
