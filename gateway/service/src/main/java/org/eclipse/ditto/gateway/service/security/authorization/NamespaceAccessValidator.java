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
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.placeholders.PipelineElement;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;


/**
 * Validates namespace access based on configured namespace access control rules.
 * Evaluates conditions (using JWT claims and HTTP headers) and checks if namespaces
 * are allowed or blocked based on pattern matching with SQL-LIKE wildcard syntax.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class NamespaceAccessValidator {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(NamespaceAccessValidator.class);

    private final List<NamespaceAccessRule> namespaceAccessRules;
    private final ExpressionResolver expressionResolver;
    private final DittoHeaders dittoHeaders;

    NamespaceAccessValidator(final List<NamespaceAccessConfig> namespaceAccessConfigs,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        this.namespaceAccessRules = namespaceAccessConfigs.stream()
                .map(NamespaceAccessRule::new)
                .toList();
        this.expressionResolver = createExpressionResolver(dittoHeaders, jwt);
        this.dittoHeaders = dittoHeaders;
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
     * If no rules are configured at all, access is allowed (backward compatible).
     * If rules are configured but none match the current request, access is denied (fail-closed).
     *
     * @param namespace the namespace to check.
     * @return {@code true} if the namespace is accessible.
     */
    public boolean isNamespaceAccessible(final String namespace) {
        if (namespaceAccessRules.isEmpty()) {
            return true;
        }

        boolean allowedByAnyRule = false;

        for (final NamespaceAccessRule rule : namespaceAccessRules) {
            if (allConditionsMet(rule.conditions())) {
                if (rule.matcher().isBlocked(namespace)) {
                    LOGGER.withCorrelationId(dittoHeaders)
                            .info("Namespace <{}> is blocked by namespace-access rule: <{}>", namespace, rule);
                    return false;
                }
                if (rule.matcher().isAllowedByRule(namespace)) {
                    allowedByAnyRule = true;
                }
            }
        }

        if (!allowedByAnyRule) {
            LOGGER.withCorrelationId(dittoHeaders)
                    .info("Namespace <{}> is not accessible: no rule granted access", namespace);
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
     * <p>
     * Returns {@code Optional.empty()} if no rules are configured (no restriction), or a matching rule
     * has an empty allowed list (all namespaces permitted by that rule).
     * Returns {@code Optional.of(emptySet)} if rules are configured but no conditions match (fail-closed),
     * or if matching rules contain only wildcard allowed namespaces (safe: prevents over-exposure).
     * Returns the collected exact patterns otherwise.
     *
     * @return an Optional containing applicable exact namespace patterns, or empty if no restrictions apply.
     */
    public Optional<Set<String>> getApplicableNamespacePatterns() {
        if (namespaceAccessRules.isEmpty()) {
            return Optional.empty();
        }

        final Set<String> exactPatterns = new HashSet<>();
        boolean anyConditionMatched = false;

        for (final NamespaceAccessRule rule : namespaceAccessRules) {
            if (allConditionsMet(rule.conditions())) {
                anyConditionMatched = true;
                final List<String> allowedNamespaces = rule.allowedNamespaces();

                if (allowedNamespaces.isEmpty()) {
                    return Optional.empty(); // this rule permits all namespaces
                }

                for (final String namespace : allowedNamespaces) {
                    if (!namespace.contains("*") && !namespace.contains("?")) {
                        exactPatterns.add(namespace);
                    }
                    // wildcards intentionally skipped: cannot be injected into search queries
                }
            }
        }

        if (!anyConditionMatched) {
            return Optional.of(Set.of()); // fail-closed: no rule matched, restrict search to nothing
        }

        // exactPatterns may be empty if allowed list contained only wildcards → safe empty set
        return Optional.of(Set.copyOf(exactPatterns));
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

    private record NamespaceAccessRule(List<String> conditions,
                                       List<String> allowedNamespaces,
                                       List<String> blockedNamespaces,
                                       NamespacePatternMatcher matcher
    ) {
        private NamespaceAccessRule(final NamespaceAccessConfig config) {
            this(config.getConditions(), config.getAllowedNamespaces(), config.getBlockedNamespaces(),
                    new NamespacePatternMatcher(config.getAllowedNamespaces(), config.getBlockedNamespaces()));
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" +
                    "conditions=" + conditions +
                    ", allowedNamespaces=" + allowedNamespaces +
                    ", blockedNamespaces=" + blockedNamespaces +
                    ']';
        }
    }

}
