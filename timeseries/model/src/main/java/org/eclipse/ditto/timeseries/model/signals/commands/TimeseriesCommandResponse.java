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
package org.eclipse.ditto.timeseries.model.signals.commands;

import org.eclipse.ditto.base.model.signals.commands.CommandResponse;

/**
 * Aggregate marker interface for all command responses produced by the Timeseries service.
 * Mirrors {@link TimeseriesCommand} on the response side: same {@value TimeseriesCommand#RESOURCE_TYPE}
 * resource type, type prefix {@code timeseries.responses:}.
 *
 * @param <T> the type of the implementing class.
 * @since 4.0.0
 */
public interface TimeseriesCommandResponse<T extends TimeseriesCommandResponse<T>>
        extends CommandResponse<T> {

    /**
     * Type prefix shared by all Timeseries command responses.
     */
    String TYPE_PREFIX = "timeseries." + CommandResponse.TYPE_QUALIFIER + ":";

    @Override
    default String getResourceType() {
        return TimeseriesCommand.RESOURCE_TYPE;
    }
}
