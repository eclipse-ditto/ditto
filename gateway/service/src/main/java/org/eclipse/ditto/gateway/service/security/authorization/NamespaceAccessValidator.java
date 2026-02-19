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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtPlaceholder;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates namespace access based on configured namespace access control rules.
 * Evaluates conditions (using JWT claims and HTTP headers) and checks if namespaces
 * are allowed or blocked based on pattern matching with SQL-LIKE wildcard syntax.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class NamespaceAccessValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamespaceAccessValidator.class);

    private final List<NamespaceAccessConfig> namespaceAccessConfigs;
    private final ExpressionResolver expressionResolver;

    NamespaceAccessValidator(final List<NamespaceAccessConfig> namespaceAccessConfigs,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        this.namespaceAccessConfigs = namespaceAccessConfigs;
        this.expressionResolver = createExpressionResolver(dittoHeaders, jwt);
    }

    private static ExpressionResolver createExpressionResolver(final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        if (jwt != null) {
            return PlaceholderFactory.newExpressionResolver(
                    PlaceholderFactory.newPlaceholderResolver(JwtPlaceholder.getInstance(), jwt),
                    PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), dittoHeaders),
                    PlaceholderFactory.newPlaceholderResolver(TimePlaceholder.getInstance(), new Object())
            );
        } else {
            return PlaceholderFactory.newExpressionResolver(
                    PlaceholderFactory.newPlaceholderResolver(PlaceholderFactory.newHeadersPlaceholder(), dittoHeaders),
                    PlaceholderFactory.newPlaceholderResolver(TimePlaceholder.getInstance(), new Object())
            );
        }
    }

    /**
     * Checks if the given namespace is accessible based on the configured rules.
     * <p>
     * Evaluation: all conditions must match (AND), blocked takes precedence, allowed across rules is OR.
     * If no rules match, access is allowed (backward compatible).
     *
     * @param namespace the namespace to check.
     * @return {@code true} if the namespace is accessible.
     */
    public boolean isNamespaceAccessible(final String namespace) {
        if (namespaceAccessConfigs.isEmpty()) {
            return true;
        }

        boolean anyRuleApplied = false;
        boolean allowedByAnyRule = false;

        for (final NamespaceAccessConfig config : namespaceAccessConfigs) {
            if (allConditionsMet(config.getConditions())) {
                anyRuleApplied = true;
                final NamespacePatternMatcher matcher = new NamespacePatternMatcher(
                        config.getAllowedNamespaces(),
                        config.getBlockedNamespaces()
                );

                if (matcher.isBlocked(namespace)) {
                    LOGGER.debug("Namespace '{}' is blocked", namespace);
                    return false;
                }

                if (matcher.isAllowedByRule(namespace)) {
                    allowedByAnyRule = true;
                }
            }
        }

        if (!anyRuleApplied) {
            return true;
        }

        if (!allowedByAnyRule) {
            LOGGER.debug("Namespace '{}' is not allowed by any matching rule", namespace);
        }
        return allowedByAnyRule;
    }

    /**
     * Filters a set of namespaces, returning only those that are accessible.
     *
     * @param namespaces the set of namespaces to filter.
     * @return a set containing only the accessible namespaces.
     */
    public Set<String> filterAllowedNamespaces(final Set<String> namespaces) {
        return namespaces.stream()
                .filter(this::isNamespaceAccessible)
                .collect(Collectors.toSet());
    }

    /**
     * Returns exact (non-wildcard) namespace patterns from matching rules for search namespace injection.
     * Returns empty if no configs, no conditions match, or only wildcard patterns are configured.
     *
     * @return an Optional containing applicable exact namespace patterns, or empty if no restrictions apply.
     */
    public Optional<Set<String>> getApplicableNamespacePatterns() {
        if (namespaceAccessConfigs.isEmpty()) {
            return Optional.empty();
        }

        final Set<String> applicablePatterns = new HashSet<>();
        boolean anyConditionMatched = false;

        for (final NamespaceAccessConfig config : namespaceAccessConfigs) {
            if (allConditionsMet(config.getConditions())) {
                anyConditionMatched = true;
                final List<String> allowedNamespaces = config.getAllowedNamespaces();

                if (allowedNamespaces.isEmpty()) {
                    return Optional.empty();
                }

                for (final String namespace : allowedNamespaces) {
                    if (!namespace.contains("*") && !namespace.contains("?")) {
                        applicablePatterns.add(namespace);
                    }
                }
            }
        }

        if (!anyConditionMatched || applicablePatterns.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(applicablePatterns);
    }

    private boolean allConditionsMet(final List<String> conditions) {
        if (conditions.isEmpty()) {
            return true;
        }

        for (final String condition : conditions) {
            try {
                final PipelineElement result = expressionResolver.resolve(condition);
                if (result.findFirst().isEmpty()) {
                    return false;
                }
            } catch (final UnresolvedPlaceholderException e) {
                LOGGER.debug("Could not resolve condition placeholder in <{}>: {}", condition, e.getMessage());
                return false;
            }
        }

        return true;
    }

}
