/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.metrics.executor;

import java.util.concurrent.ThreadFactory;

import com.typesafe.config.Config;

import akka.dispatch.DispatcherPrerequisites;
import akka.dispatch.ExecutorServiceConfigurator;
import akka.dispatch.ExecutorServiceFactory;
import akka.dispatch.ThreadPoolExecutorConfigurator;
import kamon.instrumentation.executor.ExecutorInstrumentation;

/**
 * Implementation of {@code ExecutorServiceConfigurator} that adds instrumentation using kamon-executors module.
 * Delegates to {@code ThreadPoolExecutorConfigurator} for the instantiation of {@code ExecutorService}s.
 * <p>
 * Modify the dispatcher config by replacing
 * <pre>
 *   type = Dispatcher
 *   executor = "thread-pool-executor"
 *   thread-pool-executor {
 *     ...
 *   }
 * </pre>
 * with
 * <pre>
 *   type = Dispatcher
 *   executor = "org.eclipse.ditto.internal.utils.metrics.executor.InstrumentedThreadPoolExecutorServiceConfigurator"
 *   thread-pool-executor {
 *     ...
 *   }
 * </pre>
 * to enable instrumentation and the reporting of metrics for this dispatcher.
 */
public class InstrumentedThreadPoolExecutorServiceConfigurator extends ExecutorServiceConfigurator {

    private final ExecutorServiceConfigurator delegate;

    public InstrumentedThreadPoolExecutorServiceConfigurator(final Config config,
            final DispatcherPrerequisites prerequisites) {
        super(config, prerequisites);
        delegate = new ThreadPoolExecutorConfigurator(config.getConfig("thread-pool-executor"), prerequisites);
    }

    @Override
    public ExecutorServiceFactory createExecutorServiceFactory(final String id, final ThreadFactory threadFactory) {
        return () -> {
            final ExecutorServiceFactory executorServiceFactory =
                    delegate.createExecutorServiceFactory(id, threadFactory);
            return ExecutorInstrumentation.instrument(executorServiceFactory.createExecutorService(), id);
        };
    }
}
