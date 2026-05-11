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
package org.eclipse.ditto.timeseries.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pekko.actor.Props;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.junit.Test;

/**
 * Smoke tests for the Timeseries service starter classes — verifying that the constants and
 * Props factory exposed by the runtime are wired to the api-side messaging constants.
 */
public final class TimeseriesServiceStarterTest {

    @Test
    public void serviceNameMatchesMessagingConstant() {
        assertThat(TimeseriesService.SERVICE_NAME)
                .isEqualTo(TimeseriesMessagingConstants.SERVICE_NAME);
    }

    @Test
    public void rootActorNameMatchesMessagingConstant() {
        assertThat(TimeseriesRootActor.ACTOR_NAME)
                .isEqualTo(TimeseriesMessagingConstants.ROOT_ACTOR_NAME);
    }

    @Test
    public void rootActorPropsHaveExpectedActorClass() {
        final Props props = TimeseriesRootActor.props(null, null);

        assertThat(props.actorClass().getName()).isEqualTo(TimeseriesRootActor.class.getName());
    }
}
