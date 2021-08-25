/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.concurrent.CompletionStage;

import akka.Done;

/**
 * A start- and stoppable kafka consumer stream.
 */
interface KafkaConsumerStream {

    /**
     * Starts the consumer stream.
     *
     * @return a completion stage that completes when the stream is stopped (gracefully or exceptionally).
     */
    CompletionStage<Done> start();

    /**
     * Stops the consumer stream gracefully.
     */
    void stop();

}
