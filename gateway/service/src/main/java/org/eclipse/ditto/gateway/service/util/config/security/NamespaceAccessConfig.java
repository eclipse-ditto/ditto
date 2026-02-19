/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Provides configuration settings for namespace-based access control.
 * <p>
 * Namespace access control allows restricting which namespaces can be accessed based on JWT claims or HTTP headers.
 * Each configuration entry consists of conditions that must all be true (AND semantics) and patterns for allowed
 * and blocked namespaces using SQL-LIKE wildcard syntax (e.g., "org.eclipse.*").
 * </p>
 */
@Immutable
@AllValuesAreNonnullByDefault
public interface NamespaceAccessConfig {

    /**
     * Returns the list of conditions that must all evaluate to true for this namespace access rule to apply.
     * Conditions are expressions using placeholders (e.g., "{{ jwt:iss | fn:filter('like','https://eclipse.org*') }}").
     *
     * @return the list of condition expressions.
     */
    List<String> getConditions();

    /**
     * Returns the list of namespace patterns that are allowed when conditions match.
     * Patterns support SQL-LIKE wildcard syntax (e.g., "org.eclipse.*", "concrete.namespace").
     * An empty list means no restrictions (all namespaces allowed) unless blocked-namespaces apply.
     *
     * @return the list of allowed namespace patterns.
     */
    List<String> getAllowedNamespaces();

    /**
     * Returns the list of namespace patterns that are explicitly blocked when conditions match.
     * Blocked patterns take precedence over allowed patterns.
     * Patterns support SQL-LIKE wildcard syntax (e.g., "forbidden.*").
     *
     * @return the list of blocked namespace patterns.
     */
    List<String> getBlockedNamespaces();

}
