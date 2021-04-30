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

import org.eclipse.ditto.internal.utils.health.StatusInfo;

/**
 * Supplies a {@link CompletionStage} for a health status, represented as {@link org.eclipse.ditto.internal.utils.health.StatusInfo}. <strong>NOTE:</strong> For
 * supplying an arbitrary status (not health-related), {@link StatusSupplier} should be used instead.
 *
 * @see StatusSupplier
 */
public interface StatusHealthSupplier extends Supplier<CompletionStage<StatusInfo>> {

}
