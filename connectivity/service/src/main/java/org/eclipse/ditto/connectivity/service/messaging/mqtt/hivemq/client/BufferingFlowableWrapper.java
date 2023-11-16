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

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

/**
 * Wrapper around flowable that buffers the items until it is told to stop.
 * When buffering is enabled, all subscribers get all missed items, when the
 * buffering is disabled, subscribers will get only new items.
 *
 * @param <T> type of items
 */
public class BufferingFlowableWrapper<T> implements Disposable {
    private final Flowable<T> originalFlowable;
    private final PublishSubject<T> buffered;
    private final PublishSubject<T> unbuffered;
    private final Disposable originalSubscription;
    private final Flowable<T> flowable;
    private final Disposable subscription;
    private boolean isBuffering = true;

    private BufferingFlowableWrapper(final Flowable<T> flowable) {
        this.originalFlowable = flowable;
        this.buffered = PublishSubject.<T>create();
        this.unbuffered = PublishSubject.<T>create();

        this.flowable = buffered
                .replay()
                .autoConnect()
                .mergeWith(unbuffered)
                .toFlowable(BackpressureStrategy.BUFFER);

        this.originalSubscription = flowable.subscribe(
                x -> (isBuffering ? buffered : unbuffered).onNext(x),
                e -> (isBuffering ? buffered : unbuffered).onError(e),
                () -> {
                    buffered.onComplete();
                    unbuffered.onComplete();
                    isBuffering = false;
                });
        this.subscription = this.flowable.subscribe(
                ignored -> {},
                ignored -> {});
    }

    /**
     * Creates new wrapper around provided {@code Flowable}.
     *
     * @param flowable flowable to wrap.
     * @return wrapper around the provided flowable.
     * @param <T> type of items of the flowable.
     */
    public static <T> BufferingFlowableWrapper<T> of(final Flowable<T> flowable) {
        return new BufferingFlowableWrapper<>(flowable);
    }

    /**
     * @return the {@code Flowable} which can be used to consume messages from original flowable.
     */
    public Flowable<T> toFlowable() {
        return isBuffering ? this.flowable : this.originalFlowable;
    }

    /**
     * Stops buffering items. All new subscribers using {@code toFlowable} method will get
     * only new items.
     */
    public void stopBuffering() {
        isBuffering = false;
        buffered.onComplete();
    }

    private boolean isDisposed = false;

    @Override
    public void dispose() {
        this.originalSubscription.dispose();
        this.subscription.dispose();
        isDisposed = true;
    }

    @Override
    public boolean isDisposed() {
        return isDisposed;
    }
}