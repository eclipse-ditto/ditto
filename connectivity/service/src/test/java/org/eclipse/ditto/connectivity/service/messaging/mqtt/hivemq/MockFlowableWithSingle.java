package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.hivemq.client.rx.FlowableWithSingle;
import com.hivemq.client.rx.reactivestreams.WithSingleSubscriber;

public class MockFlowableWithSingle<F, S> extends FlowableWithSingle<F, S> {

    private final List<F> flowable;
    private final S single;
    @Nullable private final Throwable error;

    public MockFlowableWithSingle(final List<F> flowable, final S single, @Nullable final Throwable error) {
        this.flowable = flowable;
        this.single = single;
        this.error = error;
    }

    @Override
    protected void subscribeBothActual(final @NotNull WithSingleSubscriber<? super F, ? super S> subscriber) {
        subscriber.onSingle(single);
        addSubscriberBehaviour(subscriber);
    }

    private void addSubscriberBehaviour(final Subscriber<? super F> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(final long requestSize) {
                if (null != error) {
                    subscriber.onError(error);
                }
                if (!flowable.isEmpty()) {
                    for (long emitedElements = 0; emitedElements < requestSize; emitedElements++) {
                        if (flowable.size() > emitedElements) {
                            subscriber.onNext(flowable.get(Long.valueOf(emitedElements).intValue()));
                        } else if (emitedElements == flowable.size()){
                            subscriber.onComplete();
                        }
                    }
                } else {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                subscriber.onComplete();
            }
        });
    }

    @Override
    protected void subscribeActual(final Subscriber<? super F> s) {
        addSubscriberBehaviour(s);
    }
}
