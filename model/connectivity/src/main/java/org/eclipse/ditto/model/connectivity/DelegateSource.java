/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Objects;
import java.util.Set;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.common.ConditionChecker;

/**
 * Abstract class that delegates to an existing {@link Source}. Used to extend the default fields of a {@link Source}
 * for a specific protocol.
 */
abstract class DelegateSource implements Source {

    protected final Source delegate;

    DelegateSource(final Source delegate) {
        this.delegate = ConditionChecker.checkNotNull(delegate, "delegate");
    }

    @Override
    public Set<String> getAddresses() {
        return delegate.getAddresses();
    }

    @Override
    public int getConsumerCount() {
        return delegate.getConsumerCount();
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        return delegate.getAuthorizationContext();
    }

    @Override
    public int getIndex() {
        return delegate.getIndex();
    }

    @Override
    public Enforcement getEnforcement() {
        return delegate.getEnforcement();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DelegateSource that = (DelegateSource) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "delegate=" + delegate +
                "]";
    }
}
