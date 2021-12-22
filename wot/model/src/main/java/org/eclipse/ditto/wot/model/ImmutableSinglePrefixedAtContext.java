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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link SinglePrefixedAtContext}.
 */
@Immutable
final class ImmutableSinglePrefixedAtContext implements SinglePrefixedAtContext {

    private final String prefix;
    private final SingleUriAtContext delegateContext;

    ImmutableSinglePrefixedAtContext(final CharSequence prefix, final SingleUriAtContext delegateContext) {
        this.prefix = checkNotNull(prefix, "prefix").toString();
        this.delegateContext = checkNotNull(delegateContext, "delegateContext");
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public SingleUriAtContext getDelegateContext() {
        return delegateContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSinglePrefixedAtContext that = (ImmutableSinglePrefixedAtContext) o;
        return Objects.equals(prefix, that.prefix) &&
                Objects.equals(delegateContext, that.delegateContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, delegateContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "prefix=" + prefix +
                ", delegateContext=" + delegateContext +
                "]";
    }
}
