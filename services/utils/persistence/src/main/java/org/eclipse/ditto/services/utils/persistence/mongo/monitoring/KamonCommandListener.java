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
package org.eclipse.ditto.services.utils.persistence.mongo.monitoring;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;

/**
 * Reports elapsed time for every MongoDB command to Kamon.
 */
public class KamonCommandListener implements CommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCommandListener.class);
    private static final String COMMAND_NAME_TAG = "command_name";
    private final String timerName;

    public KamonCommandListener(final String metricName) {
        this.timerName = ConditionChecker.argumentNotEmpty(metricName, "metricName") + "_mongodb";
    }

    @Override
    public void commandStarted(final CommandStartedEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sent command '{}:{}' with id {} to database '{}' "
                            + "on connection '{}' to server '{}'",
                    event.getCommandName(),
                    event.getCommand().get(event.getCommandName()),
                    event.getRequestId(),
                    event.getDatabaseName(),
                    event.getConnectionDescription().getConnectionId(),
                    event.getConnectionDescription().getServerAddress());
        }
    }

    @Override
    public void commandSucceeded(final CommandSucceededEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Successfully executed command '{}' with id {} "
                            + "on connection '{}' to server '{}' after {}ms",
                    event.getCommandName(),
                    event.getRequestId(),
                    event.getConnectionDescription().getConnectionId(),
                    event.getConnectionDescription().getServerAddress(),
                    event.getElapsedTime(TimeUnit.MILLISECONDS));
        }

        final long elapsedTime = event.getElapsedTime(TimeUnit.NANOSECONDS);
        final String commandName = event.getCommandName();
        recordElapsedTime(elapsedTime, commandName);
    }

    @Override
    public void commandFailed(final CommandFailedEvent event) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Failed execution of command '{}' with id {} "
                            + "on connection '{}' to server '{}'  after {}ms with exception '{}'",
                    event.getCommandName(),
                    event.getRequestId(),
                    event.getConnectionDescription().getConnectionId(),
                    event.getConnectionDescription().getServerAddress(),
                    event.getElapsedTime(TimeUnit.MILLISECONDS),
                    event.getThrowable());
        }

        final long elapsedTime = event.getElapsedTime(TimeUnit.NANOSECONDS);
        final String commandName = event.getCommandName();
        recordElapsedTime(elapsedTime, commandName);
    }

    private void recordElapsedTime(final long elapsedTime, final String commandName) {
        DittoMetrics.timer(TraceUtils.metricizeTraceUri(timerName))
                .tag(COMMAND_NAME_TAG, commandName)
                .record(elapsedTime, TimeUnit.NANOSECONDS);
    }
}
