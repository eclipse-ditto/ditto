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
package org.eclipse.ditto.internal.utils.akka.controlflow;

import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.Shape;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

/**
 * A copy of {@code Zip} that is lazy in the second inlet.
 */
public final class LazyZip<S, T> extends GraphStage<FanInShape2<S, T, Pair<S, T>>> {

    private final Inlet<S> strict = Inlet.create("strict");
    private final Inlet<T> lazy = Inlet.create("lazy");
    private final Outlet<Pair<S, T>> out = Outlet.create("out");
    private final FanInShape2<S, T, Pair<S, T>> shape = new FanInShape2<>(strict, lazy, out);

    private LazyZip() {}

    /**
     * Create a Zip component lazy in the second inlet.
     *
     * @return the zip component.
     */
    public static <S, T> LazyZip<S, T> of() {
        return new LazyZip<>();
    }

    @Override
    public FanInShape2<S, T, Pair<S, T>> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new LazyZipLogic(shape);
    }

    private final class LazyZipLogic extends GraphStageLogic {

        private LazyZipLogic(final Shape shape) {
            super(shape);
            setHandler(out, new LazyZipOutHandler());
            setHandler(strict, new LazyZipStrictHandler());
            setHandler(lazy, new LazyZipLazyHandler());
        }

        private boolean canPull(final Inlet<?> inlet) {
            return !isClosed(inlet) && !hasBeenPulled(inlet);
        }

        private final class LazyZipLazyHandler extends AbstractInHandler {

            @Override
            public void onPush() {
                push(out, Pair.create(grab(strict), grab(lazy)));
                if (canPull(strict)) {
                    pull(strict);
                }
            }
        }

        private final class LazyZipStrictHandler extends AbstractInHandler {

            @Override
            public void onPush() {
                if (canPull(lazy)) {
                    pull(lazy);
                }
            }
        }

        private final class LazyZipOutHandler extends AbstractOutHandler {

            @Override
            public void onPull() {
                if (canPull(strict)) {
                    pull(strict);
                }
            }
        }
    }
}
