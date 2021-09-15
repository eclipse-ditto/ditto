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

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.Done;

@RunWith(MockitoJUnitRunner.class)
public final class RestartableKafkaConsumerStreamTest {

    @Mock
    private ExponentialBackOffConfig exponentialBackOffConfigMock;

    @Test
    public void whenCompleteDelegatesToKafkaConsumerStream() {
        final KafkaConsumerStream kafkaConsumerStream = Mockito.mock(KafkaConsumerStream.class);
        mockExponentialBackOffConfigWith(Duration.ofMillis(100), Duration.ofSeconds(60));
        final RestartableKafkaConsumerStream restartableKafkaConsumerStream =
                new RestartableKafkaConsumerStream(() -> kafkaConsumerStream, exponentialBackOffConfigMock);
        final BiConsumer<? super Done, ? super Throwable> handler = (result, error) -> {};
        restartableKafkaConsumerStream.whenComplete(handler);
        verify(kafkaConsumerStream).whenComplete(same(handler));
    }

    @Test
    public void stopDelegatesToKafkaConsumerStream() {
        final KafkaConsumerStream kafkaConsumerStream = Mockito.mock(KafkaConsumerStream.class);
        mockExponentialBackOffConfigWith(Duration.ofMillis(100), Duration.ofSeconds(60));
        final RestartableKafkaConsumerStream restartableKafkaConsumerStream =
                new RestartableKafkaConsumerStream(() -> kafkaConsumerStream, exponentialBackOffConfigMock);
        restartableKafkaConsumerStream.stop();
        verify(kafkaConsumerStream).stop();
    }

    @Test
    public void restartStopsAndCreatesANewStream() throws InterruptedException {
        final KafkaConsumerStream kafkaConsumerStream = Mockito.mock(KafkaConsumerStream.class);
        when(kafkaConsumerStream.stop()).thenReturn(CompletableFuture.completedFuture(Done.getInstance()));
        mockExponentialBackOffConfigWith(Duration.ofMillis(100), Duration.ofSeconds(60), 1.0);
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final RestartableKafkaConsumerStream restartableKafkaConsumerStream =
                new RestartableKafkaConsumerStream(() -> {
                    countDownLatch.countDown();
                    return kafkaConsumerStream;
                }, exponentialBackOffConfigMock);
        restartableKafkaConsumerStream.restart();
        verify(kafkaConsumerStream).stop();
        countDownLatch.await(1, TimeUnit.SECONDS);
    }

    private void mockExponentialBackOffConfigWith(final Duration min, final Duration max) {
        when(exponentialBackOffConfigMock.getMin()).thenReturn(min);
        when(exponentialBackOffConfigMock.getMax()).thenReturn(max);
    }

    private void mockExponentialBackOffConfigWith(final Duration min, final Duration max, final double randomFactor) {
        when(exponentialBackOffConfigMock.getMin()).thenReturn(min);
        when(exponentialBackOffConfigMock.getMax()).thenReturn(max);
        when(exponentialBackOffConfigMock.getRandomFactor()).thenReturn(randomFactor);
    }

}
