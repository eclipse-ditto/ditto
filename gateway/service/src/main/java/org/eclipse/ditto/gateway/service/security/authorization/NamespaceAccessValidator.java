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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

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
 * <p>
 * The JWT and headers a validator resolves conditions against are fixed for its lifetime, so the outcome of a rule's
 * conditions is memoized after the first evaluation. This matters for streaming sessions, which hold one validator
 * and check every outgoing signal against it. Rules with a condition referencing the {@code time:} placeholder are
 * re-evaluated on every check, as their outcome may change over time.
 */
@ThreadSafe
@AllValuesAreNonnullByDefault
public final class NamespaceAccessValidator {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(NamespaceAccessValidator.class);

    private final List<NamespaceAccessRule> namespaceAccessRules;
    private final ExpressionResolver expressionResolver;
    private final DittoHeaders dittoHeaders;
    // index-aligned with namespaceAccessRules; null until the rule's conditions were evaluated
    private final AtomicReferenceArray<Boolean> memoizedConditionResults;

    NamespaceAccessValidator(final List<NamespaceAccessConfig> namespaceAccessConfigs,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        this(toRules(namespaceAccessConfigs), createExpressionResolver(dittoHeaders, jwt), dittoHeaders);
    }

    NamespaceAccessValidator(final List<NamespaceAccessRule> namespaceAccessRules,
            final ExpressionResolver expressionResolver,
            final DittoHeaders dittoHeaders) {
        this.namespaceAccessRules = namespaceAccessRules;
        this.expressionResolver = expressionResolver;
        this.dittoHeaders = dittoHeaders;
        memoizedConditionResults = new AtomicReferenceArray<>(namespaceAccessRules.size());
    }

    /**
     * Creates a validator for already parsed rules, avoiding to compile their namespace patterns again.
     *
     * @param namespaceAccessRules the rules to validate against.
     * @param dittoHeaders the Ditto headers containing request context.
     * @param jwt the JSON Web Token, may be null.
     * @return the validator.
     */
    static NamespaceAccessValidator of(final List<NamespaceAccessRule> namespaceAccessRules,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonWebToken jwt) {
        return new NamespaceAccessValidator(namespaceAccessRules, createExpressionResolver(dittoHeaders, jwt),
                dittoHeaders);
    }

    static List<NamespaceAccessRule> toRules(final List<NamespaceAccessConfig> namespaceAccessConfigs) {
        return namespaceAccessConfigs.stream()
                .map(NamespaceAccessRule::new)
                .toList();
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

        for (int i = 0; i < namespaceAccessRules.size(); i++) {
            final NamespaceAccessRule rule = namespaceAccessRules.get(i);
            if (conditionsMet(i)) {
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

        for (int i = 0; i < namespaceAccessRules.size(); i++) {
            final NamespaceAccessRule rule = namespaceAccessRules.get(i);
            if (conditionsMet(i)) {
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

    private boolean conditionsMet(final int ruleIndex) {
        final NamespaceAccessRule rule = namespaceAccessRules.get(ruleIndex);
        if (rule.timeDependent()) {
            return allConditionsMet(rule.conditions());
        }
        // racing threads compute the same result, so no synchronization beyond safe publication is needed
        Boolean result = memoizedConditionResults.get(ruleIndex);
        if (result == null) {
            result = allConditionsMet(rule.conditions());
            memoizedConditionResults.set(ruleIndex, result);
        }
        return result;
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

    record NamespaceAccessRule(List<String> conditions,
                               List<String> allowedNamespaces,
                               List<String> blockedNamespaces,
                               List<String> resourceTypes,
                               NamespacePatternMatcher matcher,
                               boolean timeDependent
    ) {
        private NamespaceAccessRule(final NamespaceAccessConfig config) {
            this(config.getConditions(), config.getAllowedNamespaces(), config.getBlockedNamespaces(),
                    config.getResourceTypes(),
                    new NamespacePatternMatcher(config.getAllowedNamespaces(), config.getBlockedNamespaces()),
                    isTimeDependent(config.getConditions()));
        }

        // deliberately coarse: a false positive only costs the memoization, never correctness
        private static boolean isTimeDependent(final List<String> conditions) {
            return conditions.stream().anyMatch(condition -> condition.contains(TimePlaceholder.PREFIX + ":"));
        }

        boolean appliesTo(final String resourceType) {
            return resourceTypes.isEmpty() || resourceTypes.contains(resourceType);
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
