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
package org.eclipse.ditto.model.base.acks;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Defines built-in {@link AcknowledgementLabel}s which are emitted by Ditto itself.
 * This is intentionally <em>not</em> an {@code enum} as the enum values would have difficulties to comply to the
 * {@code hashCode/equals} contract when comparing with an {@code ImmutableAcknowledgementLabel} of the same value.
 *
 * @since 1.1.0
 */
public final class DittoAcknowledgementLabel {

    /**
     * Label for Acknowledgements indicating that a change to an entity (e. g. a thing) has successfully been persisted
     * to the twin.
     */
    public static final AcknowledgementLabel PERSISTED = AcknowledgementLabel.of("twin-persisted");

    private DittoAcknowledgementLabel() {
        throw new AssertionError();
    }

    /**
     * Indicates whether the given acknowledgement label is a Ditto acknowledgement label.
     *
     * @param acknowledgementLabel the acknowledgement label to be checked.
     * @return {@code true} if the given acknowledgement label is a constant of DittoAcknowledgementLabel.
     */
    public static boolean contains(@Nullable final AcknowledgementLabel acknowledgementLabel) {
        for (final AcknowledgementLabel dittoAcknowledgementLabel : values()) {
            if (areEqual(dittoAcknowledgementLabel, acknowledgementLabel)) {
                return true;
            }
        }
        return false;
    }

    private static AcknowledgementLabel[] values() {
        return new AcknowledgementLabel[] {
                PERSISTED
        };
    }

    private static boolean areEqual(final AcknowledgementLabel dittoAcknowledgementLabel,
            @Nullable final AcknowledgementLabel other) {

        return Objects.equals(dittoAcknowledgementLabel, other);
    }

}
