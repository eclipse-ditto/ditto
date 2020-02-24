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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link DittoAcknowledgementLabel}.
 */
public final class DittoAcknowledgementLabelTest {

    @Test
    public void containsNullIsFalse() {
        assertThat(DittoAcknowledgementLabel.contains(null)).isFalse();
    }

    @Test
    public void doesNotContainGivenLabel() {
        final AcknowledgementLabel acknowledgementLabel = AcknowledgementLabel.of("abc");

        assertThat(DittoAcknowledgementLabel.contains(acknowledgementLabel)).isFalse();
    }

    @Test
    public void containsConstantOfSelf() {
        assertThat(DittoAcknowledgementLabel.contains(DittoAcknowledgementLabel.PERSISTED)).isTrue();
    }

    @Test
    public void containsGivenLabel() {
        final AcknowledgementLabel acknowledgementLabel =
                AcknowledgementLabel.of(DittoAcknowledgementLabel.PERSISTED.toString());

        assertThat(DittoAcknowledgementLabel.contains(acknowledgementLabel)).isTrue();
    }

}