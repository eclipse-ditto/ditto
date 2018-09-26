/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.health.status;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.ditto.services.utils.health.StatusInfo;

/**
 * Supplies a {@link CompletionStage} for a health status, represented as {@link StatusInfo}. <strong>NOTE:</strong> For
 * supplying an arbitrary status (not health-related), {@link StatusSupplier} should be used instead.
 *
 * @see StatusSupplier
 */
public interface StatusHealthSupplier extends Supplier<CompletionStage<StatusInfo>> {

}
