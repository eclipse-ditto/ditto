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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.connectivity.messaging.ReconnectActor}.
 */
public final class ReconnectActorTest {

    @Test
    public void conversionBetweenCorrelationIdAndConnectionIdIsOneToOne() {
        final String id1 = "random-connection-ID-jbxlkeimx";
        final String id2 = "differentConnectionId";
        final Optional<String> outputId1 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id1));
        final Optional<String> outputId2 = ReconnectActor.toConnectionId(ReconnectActor.toCorrelationId(id2));
        assertThat(outputId1).contains(id1);
        assertThat(outputId2).contains(id2);
        assertThat(outputId1).isNotEqualTo(outputId2);
    }
}
