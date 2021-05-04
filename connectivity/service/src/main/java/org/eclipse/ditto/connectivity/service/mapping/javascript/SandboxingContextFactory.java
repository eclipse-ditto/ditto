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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import java.time.Duration;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

/**
 * Special Rhino ContextFactory responsible for sandboxing JavaScript execution.
 */
final class SandboxingContextFactory extends ContextFactory {

    /**
     * Make Rhino runtime to call observeInstructionCount each 10000 bytecode instructions.
     */
    private static final int INSTRUCTION_OBSERVER_THRESHOLD = 10000;

    /**
     * Use pure interpreter mode, otherwise max script exec time observation won't work.
     */
    private static final int OPTIMIZATION_LEVEL = -1;

    private final Duration maxScriptExecutionTime;
    private final int maxStackDepth;

    /**
     * Constructs a new ContextFactory for sandboxing Rhino executions.
     *
     * @param maxScriptExecutionTime the maximum execution time of a mapping script to run.
     * Prevents endless loops and too complex scripts.
     * @param maxStackDepth the maximum call stack depth in the mapping script. Prevents recursions or other too complex
     * computation.
     */
    SandboxingContextFactory(final Duration maxScriptExecutionTime, final int maxStackDepth) {
        this.maxScriptExecutionTime = maxScriptExecutionTime;
        this.maxStackDepth = maxStackDepth;
    }

    @Override
    protected Context makeContext() {
        final StartTimeAwareContext cx = new StartTimeAwareContext(this);
        cx.setOptimizationLevel(OPTIMIZATION_LEVEL);
        cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD);
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setMaximumInterpreterStackDepth(maxStackDepth);
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
        final StartTimeAwareContext context = (StartTimeAwareContext) cx;
        final long currentTime = System.currentTimeMillis();
        if (currentTime - context.startTime > maxScriptExecutionTime.toMillis()) {
            throw new Error("Maximum execution time of <" + maxScriptExecutionTime.toMillis() + ">ms was exceeded.");
        }
    }

    @Override
    protected Object doTopCall(final Callable callable, final Context cx, final Scriptable scope,
            final Scriptable thisObj, final Object[] args) {
        final StartTimeAwareContext mcx = (StartTimeAwareContext) cx;
        mcx.startTime = System.currentTimeMillis();

        return super.doTopCall(callable, cx, scope, thisObj, args);
    }

    /**
     * Custom Context to store execution time.
     */
    private static class StartTimeAwareContext extends Context {

        private long startTime;

        private StartTimeAwareContext(final ContextFactory factory) {
            super(factory);
        }
    }

}
