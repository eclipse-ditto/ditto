/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of {@link org.eclipse.ditto.base.model.acks.AcknowledgementRequest}.
 */
@Immutable
final class ImmutableAcknowledgementRequest implements AcknowledgementRequest {

    private final AcknowledgementLabel label;

    private ImmutableAcknowledgementRequest(final AcknowledgementLabel label) {
        this.label = label;
    }

    /**
     * Returns an instance of {@code ImmutableAcknowledgementRequest}.
     *
     * @param acknowledgementLabel the label of the returned acknowledgement request.
     * @return the instance.
     * @throws NullPointerException if {@code acknowledgmentLabel} is {@code null}.
     */
    public static ImmutableAcknowledgementRequest getInstance(final AcknowledgementLabel acknowledgementLabel) {
        return new ImmutableAcknowledgementRequest(checkNotNull(acknowledgementLabel, "acknowledgementLabel"));
    }

    @Override
    public AcknowledgementLabel getLabel() {
        return label;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAcknowledgementRequest that = (ImmutableAcknowledgementRequest) o;
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    @Override
    public String toString() {
        return label.toString();
    }

}
