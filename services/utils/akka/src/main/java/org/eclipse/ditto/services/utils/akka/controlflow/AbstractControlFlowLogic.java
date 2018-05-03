/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.akka.controlflow;

import akka.stream.Inlet;
import akka.stream.Shape;
import akka.stream.stage.GraphStageLogicWithLogging;

/**
 * Make it convenient to define {@code GraphStageLogic} for processing units of a control flow.
 * Correct usage:
 * <pre>
 * {@code
 * class Example extends GraphStage<SHAPE> {
 *     public GraphStageLogic createLogic(final Attributes inheritedAttributes) {
 *         return new ControlFlowLogic(shape) {
 *             {
 *                 // MANDATORY: call 'initOutlets' in initializer block
 *                 // because outlet handlers must be set before constructor of GraphStageLogic returns
 *                 initOutlets(shape);
 *
 *                 // Define message handler for all inlets. Use 'emit' to pass elements downstream.
 *                 // DO NOT call 'pull', 'push' or 'setHandler'.
 *                 when(shape.someInlet(), incomingMessage -> {
 *                    final Object outgoingMessage = process(incomingMessage);
 *                    emit(shape.someOutlet(), outgoingMessage);
 *                    log().info("Good job!");
 *                 });
 *             }
 *         }
 *     }
 * }
 * }
 * </pre>
 */
public abstract class AbstractControlFlowLogic extends GraphStageLogicWithLogging {

    private final Shape shape;

    /**
     * Create a processing unit of a control flow.
     *
     * @param shape shape of the processing unit.
     */
    protected AbstractControlFlowLogic(final Shape shape) {
        super(shape);
        this.shape = shape;
    }

    /**
     * Define an inlet handler unburdened by low-level stream primitives. DO NOT call {@code setHandler} directly.
     *
     * @param <T> type of messages from {@code inlet}.
     * @param inlet an inlet of this processing unit.
     * @param emitHandler what to do when a message passes from inlet.
     */
    protected <T> void when(final Inlet<T> inlet, final Emitter<T> emitHandler) {
        setHandler(inlet, () -> {
            emitHandler.handle(grab(inlet));
            pull(inlet);
        });
    }

    /**
     * MUST be called in an initializer block. Set outlet handlers to do nothing.
     *
     * @param shape shape of this processing unit.
     */
    protected void initOutlets(final Shape shape) {
        shape.getOutlets().forEach(outlet -> setHandler(outlet, () -> {}));
    }

    /**
     * Pull all inlet at the start.
     */
    @Override
    public void preStart() {
        shape.getInlets().forEach(this::pull);
    }

    /**
     * Functional interface for message handlers permitting the throwing of all exceptions.
     *
     * @param <T> type of messages to handle.
     */
    @FunctionalInterface
    public interface Emitter<T> {

        /**
         * Handle a message. CAUTION: If an output is generated for an outlet, call {@code emit} to pass it
         * downstream. DO NOT call {@code push} or {@code pull}.
         */
        void handle(final T input) throws Exception;
    }
}
