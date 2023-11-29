/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;

/**
 * Unit test for {@link BufferingFlowableWrapper}.
 */
public class BufferingFlowableWrapperTest {

    private final PublishSubject<Integer> emitter = PublishSubject.create();
    private final Flowable<Integer> flowable = emitter.toFlowable(BackpressureStrategy.DROP);
    private final BufferingFlowableWrapper<Integer> bufferingFlowableWrapper = BufferingFlowableWrapper.of(flowable);

    @Test
    public void newInstanceWithNullGenericMqttSubscribingClientThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> BufferingFlowableWrapper.of(null))
                .withMessage("The flowable must not be null!")
                .withNoCause();
    }

    @Test
    public void toFlowableEmitsErrors() {
        final var errors = new ArrayList<Throwable>();
        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(
                ignored -> {},
                errors::add);

        final var error = new Exception();
        emitter.onError(error);

        subscription.dispose();

        assertThat(errors).containsExactly(error);
    }

    @Test
    public void toFlowableEmitsOnCompletedWhenOriginalFlowableCompletes() {
        final var completes = new ArrayList<Boolean>();
        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(
                ignored -> {},
                ignored -> {},
                () -> completes.add(true));

        emitter.onComplete();

        subscription.dispose();

        assertThat(completes).containsExactly(true);
    }

    @Test
    public void toFlowableEmitsItemsEmittedBeforeSubscription() {
        final var received = new ArrayList<Integer>();

        emitter.onNext(1);

        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(received::add);
        subscription.dispose();

        assertThat(received).containsExactly(1);
    }

    @Test
    public void toFlowableEmitsItemsAfterBufferingIsStopped() {
        final var received = new ArrayList<Integer>();

        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(received::add);
        emitter.onNext(1);
        bufferingFlowableWrapper.stopBuffering();
        emitter.onNext(2);
        subscription.dispose();

        assertThat(received).containsExactly(1, 2);
    }

    @Test
    public void toFlowableDoesNotEmitPreviousItemsAfterBufferingIsStopped() {
        emitter.onNext(1);
        bufferingFlowableWrapper.stopBuffering();

        final var received = new ArrayList<Integer>();
        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(received::add);
        emitter.onNext(2);
        subscription.dispose();

        assertThat(received).containsExactly(2);
    }

    @Test
    public void toFlowableEmitsPreviousItemsToAllSubscribers() {
        final var received1 = new ArrayList<Integer>();
        final var received2 = new ArrayList<Integer>();
        final var received3 = new ArrayList<Integer>();

        emitter.onNext(1);
        final var subscription1 = bufferingFlowableWrapper.toFlowable().subscribe(received1::add);
        emitter.onNext(2);
        final var subscription2 = bufferingFlowableWrapper.toFlowable().subscribe(received2::add);
        emitter.onNext(3);
        final var subscription3 = bufferingFlowableWrapper.toFlowable().subscribe(received3::add);
        subscription1.dispose();
        subscription2.dispose();
        subscription3.dispose();

        assertThat(received1).containsExactly(1, 2, 3);
        assertThat(received2).containsExactly(1, 2, 3);
        assertThat(received3).containsExactly(1, 2, 3);
    }

    @Test
    public void toFlowableOnDisposedWrapperThrowsException() {
        bufferingFlowableWrapper.dispose();
        Assertions.assertThatIllegalStateException()
                .isThrownBy(bufferingFlowableWrapper::toFlowable)
                .withMessage("The wrapper is disposed.")
                .withNoCause();
    }

    @Test
    public void stopBufferingOnDisposedWrapperThrowsException() {
        bufferingFlowableWrapper.dispose();
        Assertions.assertThatIllegalStateException()
                .isThrownBy(bufferingFlowableWrapper::stopBuffering)
                .withMessage("The wrapper is disposed.")
                .withNoCause();
    }

    @Test
    public void toFlowableEmitsAllItemsFromCompletedFlowable() {
        final var bufferingFlowableWrapper = BufferingFlowableWrapper.of(Flowable.just(1));

        final var received = new ArrayList<Integer>();
        final var subscription = bufferingFlowableWrapper.toFlowable().subscribe(received::add);
        subscription.dispose();

        assertThat(received).containsExactly(1);
    }

}