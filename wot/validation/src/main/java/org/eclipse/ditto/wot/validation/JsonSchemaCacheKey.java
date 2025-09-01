/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.validation;

import org.eclipse.ditto.wot.model.SingleDataSchema;

/**
 * Provides a cache key for caching {@link SingleDataSchema} to JsonSchema instances.
 * @param dataSchema
 * @param validateRequiredObjectFields
 * @since 3.8.0
 */
public record JsonSchemaCacheKey(SingleDataSchema dataSchema, boolean validateRequiredObjectFields) {}
