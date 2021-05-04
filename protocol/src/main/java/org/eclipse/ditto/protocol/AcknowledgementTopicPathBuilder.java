/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol;

/**
 * Builder to create a topic path for Acknowledgements.
 *
 * @since 1.1.0
 */
public interface AcknowledgementTopicPathBuilder extends TopicPathBuildable {

    /**
     * Sets a custom Acknowledgement label on the topic path.
     *
     * @param label the Acknowledgement label to set on the topic path.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code label} is {@code null}.
     */
    AcknowledgementTopicPathBuilder label(CharSequence label);

    /**
     * Remove the custom Acknowledgement label from the topic path to signify aggregated acks.
     *
     * @return this builder to allow method chaining.
     */
    AcknowledgementTopicPathBuilder aggregatedAcks();
}
