/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.connectivity.messaging.monitoring;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.connectivity.Connection;

/**
 * Registry that provides monitors for the different use cases inside a connection.
 * @param <T> type of the monitor.
 */
public interface ConnectionMonitorRegistry<T> {

    /**
     * Initialize all monitors for the {@code connection}.
     * @param connection the connection.
     */
    void initForConnection(final Connection connection);

    /**
     * Removes all monitors for the {@code connection} from the global registry.
     * @param connection the connection.
     */
    void resetForConnection(final Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#DISPATCHED} messages.
     *
     * @param connectionId connection id
     * @param target the target address
     * @return the counter
     */
    T forOutboundDispatched(final EntityId connectionId, final String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#FILTERED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound filtered counter
     */
    T forOutboundFiltered(final EntityId connectionId, final String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#PUBLISHED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound published counter
     */
    T forOutboundPublished(final EntityId connectionId, final String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#CONSUMED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound counter
     */
    T forInboundConsumed(final EntityId connectionId, final String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#MAPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound mapped counter
     */
    T forInboundMapped(final EntityId connectionId, final String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#ENFORCED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound enforced counter
     */
    T forInboundEnforced(final EntityId connectionId, final String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#DROPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound dropped counter
     */
    T forInboundDropped(final EntityId connectionId, final String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#DISPATCHED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response consumed counter
     */
    T forResponseDispatched(final EntityId connectionId);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#DROPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response dropped counter
     */
    T forResponseDropped(final EntityId connectionId);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#MAPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response mapped counter
     */
    T forResponseMapped(final EntityId connectionId);

    /**
     * Gets counter for {@link org.eclipse.ditto.model.connectivity.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.model.connectivity.MetricType#PUBLISHED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response published counter
     */
    T forResponsePublished(final EntityId connectionId);

}
