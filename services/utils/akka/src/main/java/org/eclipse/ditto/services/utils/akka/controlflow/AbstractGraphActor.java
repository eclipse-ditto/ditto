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
package org.eclipse.ditto.services.utils.akka.controlflow;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.MergeHub;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Actor whose behavior is defined entirely by an Akka stream graph.
 */
public abstract class AbstractGraphActor<T> extends AbstractActor {

    protected final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    protected final ActorMaterializer materializer = ActorMaterializer.create(getContext());

    private final Sink<T, NotUsed> messageHandler = MergeHub.of(getMessageClass()).to(getHandler()).run(materializer);

    protected abstract Class<T> getMessageClass();

    protected abstract Source<T, ?> mapMessage(final Object message);

    protected abstract Sink<T, ?> getHandler();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(message -> {
                    log.debug("Received message: <{}>.", message);
                    mapMessage(message).runWith(messageHandler, materializer);
                })
                .build();
    }
}
