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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.eclipse.ditto.internal.utils.health.AbstractHealthCheckingActor;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;

/**
 * Reports the health of the {@link TimeseriesAdapter} in the service's {@code /health} status. It does not affect
 * {@code /ready}, which is based on cluster membership.
 */
final class TimeseriesAdapterHealthChecker extends AbstractHealthCheckingActor {

    private final TimeseriesAdapter adapter;

    @SuppressWarnings("unused")
    private TimeseriesAdapterHealthChecker(final TimeseriesAdapter adapter) {
        this.adapter = adapter;
    }

    static Props props(final TimeseriesAdapter adapter) {
        return Props.create(TimeseriesAdapterHealthChecker.class, adapter);
    }

    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(HealthStatus.class, this::applyHealth)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        final ActorRef self = getSelf();
        CompletionStage<HealthStatus> check;
        try {
            check = adapter.checkHealth();
        } catch (final RuntimeException e) {
            check = CompletableFuture.failedFuture(e);
        }
        check.handle((health, error) -> error == null ? health : HealthStatus.DOWN)
                .thenAccept(health -> self.tell(health, ActorRef.noSender()));
    }

    private void applyHealth(final HealthStatus health) {
        updateHealth(StatusInfo.fromStatus(health == HealthStatus.DOWN ? StatusInfo.Status.DOWN : StatusInfo.Status.UP,
                "timeseries adapter: " + health));
    }

}
