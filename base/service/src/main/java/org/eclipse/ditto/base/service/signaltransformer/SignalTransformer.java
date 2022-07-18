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
package org.eclipse.ditto.base.service.signaltransformer;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

/**
 * Extension which transforms a received {@link Signal} (at the "edge") to a CompletionStage of a transformed Signal,
 * e.g. in order to enhance the Signal.
 */
@FunctionalInterface
public interface SignalTransformer extends Function<Signal<?>, CompletionStage<Signal<?>>>, DittoExtensionPoint {}
