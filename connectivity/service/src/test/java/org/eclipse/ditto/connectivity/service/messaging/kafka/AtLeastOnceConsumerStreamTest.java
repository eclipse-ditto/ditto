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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.junit.Test;

import akka.kafka.javadsl.Consumer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;

public final class AtLeastOnceConsumerStreamTest {

    @Test
    public void isImmutable() {
        assertInstancesOf(AtLeastOnceConsumerStream.class,
                areImmutable(),
                provided(ConnectionMonitor.class, Sink.class, Materializer.class,
                        Consumer.DrainingControl.class).areAlsoImmutable());
    }

}
