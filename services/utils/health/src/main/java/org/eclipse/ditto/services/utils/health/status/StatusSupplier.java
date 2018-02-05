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
package org.eclipse.ditto.services.utils.health.status;

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
