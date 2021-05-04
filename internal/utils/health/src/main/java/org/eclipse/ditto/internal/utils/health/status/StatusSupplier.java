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
package org.eclipse.ditto.internal.utils.health.status;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonObject;

/**
 * Supplies a {@link CompletionStage} for a status, consisting of an arbitrary JSON object. <strong>NOTE:</strong> For
 * supplying a health status, the interface {@link StatusHealthSupplier} should be used instead.
 *
 * @see StatusHealthSupplier
 */
public interface StatusSupplier extends Supplier<CompletionStage<JsonObject>> {

}
