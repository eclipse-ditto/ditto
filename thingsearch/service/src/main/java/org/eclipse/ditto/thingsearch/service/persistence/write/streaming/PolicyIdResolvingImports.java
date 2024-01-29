/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import org.eclipse.ditto.policies.model.PolicyId;

/**
 * Package private cache key for loading policies via {@link ResolvedPolicyCacheLoader} into a policy cache.
 */
record PolicyIdResolvingImports(PolicyId policyId, boolean resolveImports) {}
