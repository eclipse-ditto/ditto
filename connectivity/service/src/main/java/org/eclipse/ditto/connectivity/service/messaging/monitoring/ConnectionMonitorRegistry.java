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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import org.eclipse.ditto.connectivity.model.Connection;

/**
 * Registry that provides monitors for the different use cases inside a connection.
 * @param <T> type of the monitor.
 */
public interface ConnectionMonitorRegistry<T> {

    /**
     * Initialize all monitors for the {@code connection}.
     * @param connection the connection.
     */
    void initForConnection(Connection connection);

    /**
     * Removes all monitors for the {@code connection} from the global registry.
     * @param connection the connection.
     */
    void resetForConnection(Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#DISPATCHED} messages.
     *
     * @param connection connection
     * @param target the target address
     * @return the counter
     */
    T forOutboundDispatched(Connection connection, String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#FILTERED} messages.
     *
     * @param connection connection
     * @param target the target
     * @return the outbound filtered counter
     */
    T forOutboundFiltered(Connection connection, String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#PUBLISHED} messages.
     *
     * @param connection connection
     * @param target the target
     * @return the outbound published counter
     */
    T forOutboundPublished(Connection connection, String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#DROPPED} messages.
     *
     * @param connection connection
     * @param target the target
     * @return the outbound dropped counter
     */
    T forOutboundDropped(Connection connection, String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#ACKNOWLEDGED} messages.
     *
     * @param connection connection
     * @param target the target address
     * @return the counter
     */
    T forOutboundAcknowledged(Connection connection, String target);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#CONSUMED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound counter
     */
    T forInboundConsumed(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#MAPPED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound mapped counter
     */
    T forInboundMapped(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#DROPPED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound dropped counter
     */
    T forInboundDropped(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#ENFORCED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound enforced counter
     */
    T forInboundEnforced(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#ACKNOWLEDGED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound counter
     */
    T forInboundAcknowledged(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#INBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#THROTTLED} messages.
     *
     * @param connection connection
     * @param source the source
     * @return the inbound counter
     */
    T forInboundThrottled(Connection connection, String source);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#DISPATCHED} messages for responses.
     *
     * @param connection connection
     * @return the response consumed counter
     */
    T forResponseDispatched(Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#DROPPED} messages for responses.
     *
     * @param connection connection
     * @return the response dropped counter
     */
    T forResponseDropped(Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#MAPPED} messages for responses.
     *
     * @param connection connection
     * @return the response mapped counter
     */
    T forResponseMapped(Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#PUBLISHED} messages for responses.
     *
     * @param connection connection
     * @return the response published counter
     */
    T forResponsePublished(Connection connection);

    /**
     * Gets counter for {@link org.eclipse.ditto.connectivity.model.MetricDirection#OUTBOUND}/{@link
     * org.eclipse.ditto.connectivity.model.MetricType#ACKNOWLEDGED} messages for responses.
     *
     * @param connection connection
     * @return the counter
     */
    T forResponseAcknowledged(Connection connection);
}
