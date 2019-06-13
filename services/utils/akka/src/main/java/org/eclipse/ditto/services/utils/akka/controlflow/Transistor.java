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
package org.eclipse.ditto.services.utils.akka.controlflow;

import java.util.LinkedList;
import java.util.Queue;

import akka.stream.Attributes;
import akka.stream.FanInShape2;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.GraphStageLogicWithLogging;

/**
 * Flow of elements regulated by credits arriving at a side channel.
 * TODO: document in the manner of standard stream components.
 */
public final class Transistor<T> extends GraphStage<FanInShape2<T, Integer, T>> {

    private final Inlet<T> source = Inlet.create("source");
    private final Inlet<Integer> gate = Inlet.create("gate");
    private final Outlet<T> drain = Outlet.create("drain");
    private final FanInShape2<T, Integer, T> shape = new FanInShape2<>(source, gate, drain);

    private Transistor() {}

    /**
     * Get a transistor component.
     *
     * @param <T> element type.
     * @return a transistor.
     */
    public static <T> Transistor<T> of() {
        return new Transistor<>();
    }

    @Override
    public FanInShape2<T, Integer, T> shape() {
        return shape;
    }

    @Override
    public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
        return new TransistorLogic();
    }

    private final class TransistorLogic extends GraphStageLogicWithLogging {

        // how many times I can pull
        private int credit = 0;

        // how many times I can push
        private int demand = 0;

        // elements in flight
        private Queue<T> inflight = new LinkedList<>();

        private TransistorLogic() {
            super(shape);

            setHandler(gate, new AbstractInHandler() {
                @Override
                public void onPush() {
                    final int newCredit = grab(gate);
                    log().debug("credit = {} + {}", credit, newCredit);
                    credit += newCredit;
                    pull(gate);
                    considerPullSource();
                }
            });

            setHandler(source, new AbstractInHandler() {
                @Override
                public void onPush() {
                    final T element = grab(source);
                    log().debug("grabbed {}", element);
                    inflight.add(element);
                    considerPushDrain();
                    considerPullSource();
                }
            });

            setHandler(drain, new AbstractOutHandler() {
                @Override
                public void onPull() {
                    demand++;
                    considerPushDrain();
                    considerPullSource();
                }
            });
        }

        @Override
        public void preStart() {
            pull(gate);
        }

        private void considerPullSource() {
            if (credit > 0 & demand > 0 && inflight.isEmpty()) {
                if (!hasBeenPulled(source)) {
                    credit--;
                    log().debug("pulling; {} credit left", credit);
                    pull(source);
                }
            }
        }

        private void considerPushDrain() {
            if (demand > 0 && !inflight.isEmpty()) {
                demand--;
                final T element = inflight.poll();
                log().debug("pushing {}; {} demand left", element, demand);
                push(drain, element);
            }
        }
    }
}
