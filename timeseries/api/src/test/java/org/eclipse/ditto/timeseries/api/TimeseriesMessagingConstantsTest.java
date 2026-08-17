/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link TimeseriesMessagingConstants}.
 */
public final class TimeseriesMessagingConstantsTest {

    @Test
    public void serviceNameIsTimeseries() {
        assertThat(TimeseriesMessagingConstants.SERVICE_NAME).isEqualTo("timeseries");
    }

    @Test
    public void clusterRoleMatchesServiceName() {
        assertThat(TimeseriesMessagingConstants.CLUSTER_ROLE)
                .isEqualTo(TimeseriesMessagingConstants.SERVICE_NAME);
    }

    @Test
    public void rootActorPathStartsWithUserPath() {
        assertThat(TimeseriesMessagingConstants.ROOT_ACTOR_PATH).startsWith("/user/");
    }

    @Test
    public void rootActorPathEndsWithRootActorName() {
        assertThat(TimeseriesMessagingConstants.ROOT_ACTOR_PATH)
                .endsWith(TimeseriesMessagingConstants.ROOT_ACTOR_NAME);
    }
}
