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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper.javascript;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

/**
 *
 */
final class RhinoContextFactory extends ContextFactory {

    private static final int INSTRUCTION_OBSERVER_THRESHOLD = 1000;
    private static final int OPTIMIZATION_LEVEL = -1;

    private static final int MAX_SCRIPT_EXEC_TIME_MS = 1000;
    private static final int MAXIMUM_INTERPRETER_STACK_DEPTH = 10;

    static {
        // Initialize GlobalFactory with custom factory
        ContextFactory.initGlobal(new RhinoContextFactory());
    }

    @Override
    protected Context makeContext() {
        final RhinoContext cx = new RhinoContext(this);
        // Use pure interpreter mode to allow for observeInstructionCount(Context, int) to work
        cx.setOptimizationLevel(OPTIMIZATION_LEVEL);
        // Make Rhino runtime to call observeInstructionCount each 10000 bytecode instructions
        cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD);
        cx.setLanguageVersion(Context.VERSION_1_8);
        cx.setMaximumInterpreterStackDepth(MAXIMUM_INTERPRETER_STACK_DEPTH);
        cx.initSafeStandardObjects();
        return cx;
    }

    @Override
    public boolean hasFeature(final Context cx, final int featureIndex) {
        switch (featureIndex) {
            case Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME:
                return true;
        }
        return super.hasFeature(cx, featureIndex);
    }

    @Override
    protected void observeInstructionCount(final Context cx, final int instructionCount) {
        final RhinoContext mcx = (RhinoContext) cx;
        final long currentTime = System.currentTimeMillis();
        if (currentTime - mcx.startTime > MAX_SCRIPT_EXEC_TIME_MS) {
            // More then x milliseconds of Context creation time: it is time to stop the script.
            // Throw Error instance to ensure that script will never get control back through catch or finally.
            throw new Error();
        }
    }

    @Override
    protected Object doTopCall(final Callable callable, final Context cx, final Scriptable scope,
            final Scriptable thisObj, final Object[] args) {
        final RhinoContext mcx = (RhinoContext) cx;
        mcx.startTime = System.currentTimeMillis();

        return super.doTopCall(callable, cx, scope, thisObj, args);
    }

    /**
     * Custom Context to store execution time.
     */
    private static class RhinoContext extends Context {

        private long startTime;

        private RhinoContext(final ContextFactory factory) {
            super(factory);
        }
    }

}
