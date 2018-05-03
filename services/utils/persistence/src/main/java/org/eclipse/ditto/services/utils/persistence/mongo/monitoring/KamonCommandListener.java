/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.utils.persistence.mongo.monitoring;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;

import kamon.Kamon;
import kamon.metric.instrument.Time;

/**
 * Reports elapsed time for every MongoDB command to Kamon.
 */
public class KamonCommandListener implements CommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(KamonCommandListener.class);
    private final String histogramPrefix;

    public KamonCommandListener(final String metricName) {
        this.histogramPrefix = ConditionChecker.argumentNotEmpty(metricName, "metricName") + ".mongodb.";
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
        Kamon.metrics().histogram(histogramPrefix + commandName, Time.Nanoseconds()).record(elapsedTime);
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
        Kamon.metrics().histogram(histogramPrefix + commandName, Time.Nanoseconds()).record(elapsedTime);
    }
}