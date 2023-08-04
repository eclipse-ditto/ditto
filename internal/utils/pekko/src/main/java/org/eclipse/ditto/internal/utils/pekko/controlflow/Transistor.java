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
package org.eclipse.ditto.internal.utils.pekko.controlflow;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.FanInShape2;
import org.apache.pekko.stream.Inlet;
import org.apache.pekko.stream.Outlet;
import org.apache.pekko.stream.stage.AbstractInHandler;
import org.apache.pekko.stream.stage.AbstractOutHandler;
import org.apache.pekko.stream.stage.GraphStage;
import org.apache.pekko.stream.stage.GraphStageLogic;
import org.apache.pekko.stream.stage.GraphStageLogicWithLogging;

/**
 * Flow of elements regulated by credits arriving at a side channel.
 * The inlets/outlets (terminals) are named after a bipolar junction transistor.
 * <ul>
 * <li>Collector: Inflow of elements.</li>
 * <li>Emitter: Outflow of elements.</li>
 * <li>Base: Inflow of integers to limit the number of elements going from collector to emitter.</li>
 * </ul>
 *
 * <pre>
 * {@code
 *                     Collector<T>
 *                          +
 *                          |
 *                          |
 *                    ++    |
 *                    ||    |
 *                    ||<---+
 *                    ||
 * Base<Integer> +--->||
 *                    ||
 *                    ||+---+
 *                    ||    |
 *                    ++    |
 *                          |
 *                          |
 *                          v
 *                      Emitter<T>
 * }
 * </pre>
 */
public final class Transistor<T> extends GraphStage<FanInShape2<T, Integer, T>> {

    private final Inlet<T> collector = Inlet.create("collector");
    private final Inlet<Integer> base = Inlet.create("base");
    private final Outlet<T> emitter = Outlet.create("emitter");
    private final FanInShape2<T, Integer, T> shape = new FanInShape2<>(collector, base, emitter);

    private Transistor() {
        // no-op
    }

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

        // how many times I am allowed to pull
        private int credit = 0;

        // how many times I can push
        private int demand = 0;

        // elements in flight
        private Queue<T> inflight = new LinkedList<>();

        private TransistorLogic() {
            super(shape);

            setHandler(base, new AbstractInHandler() {
                @Override
                public void onPush() {
                    final int newCredit = grab(base);
                    log().debug("credit: {} -> {}", credit, newCredit);
                    credit = newCredit;
                    considerPullSourceAndBase();
                }
            });

            setHandler(collector, new AbstractInHandler() {
                @Override
                public void onPush() {
                    final T element = grab(collector);
                    log().debug("grabbed {}", element);
                    inflight.add(element);
                    considerPushDrain();
                    considerPullSourceAndBase();
                }
            });

            setHandler(emitter, new AbstractOutHandler() {
                @Override
                public void onPull() {
                    demand++;
                    considerPushDrain();
                    considerPullSourceAndBase();
                }
            });
        }

        @Override
        public void preStart() {
            pull(base);
        }

        private void considerPullSourceAndBase() {
            if (credit > 0 && demand > 0 && inflight.isEmpty()) {
                if (!hasBeenPulled(collector)) {
                    credit--;
                    log().debug("pulling; {} credit left", credit);
                    pull(collector);
                }
            }
            // pull base if no credit left
            if (credit <= 0 && !hasBeenPulled(base)) {
                pull(base);
            }
        }

        private void considerPushDrain() {
            if (demand > 0 && !inflight.isEmpty()) {
                demand--;
                final T element = inflight.poll();
                log().debug("pushing {}; {} demand left", element, demand);
                push(emitter, element);
            }
        }
    }
}
