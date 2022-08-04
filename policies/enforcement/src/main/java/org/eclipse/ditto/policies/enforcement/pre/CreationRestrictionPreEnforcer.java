/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.pre;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.EntityNotCreatableException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.enforcement.config.CreationRestrictionConfig;
import org.eclipse.ditto.policies.enforcement.config.DefaultEntityCreationConfig;
import org.eclipse.ditto.policies.enforcement.config.EntityCreationConfig;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Pre-Enforcer for evaluating if creation of new entities should be restricted.
 */
@Immutable
public final class CreationRestrictionPreEnforcer implements PreEnforcer {

    private static final ThreadSafeDittoLogger LOG =
            DittoLoggerFactory.getThreadSafeLogger(CreationRestrictionPreEnforcer.class);

    private final EntityCreationConfig config;

    /**
     * Constructs a new instance of CreationRestrictionPreEnforcer extension.
     *
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public CreationRestrictionPreEnforcer(final ActorSystem actorSystem, final Config config) {
        this.config = DefaultEntityCreationConfig.of(config);
    }

    boolean canCreate(final Context context) {
        return matchesList(this.config.getGrant(), context)
                && !matchesList(this.config.getRevoke(), context);
    }

    private boolean matchesList(final List<CreationRestrictionConfig> list, final Context context) {
        return list.stream().anyMatch(entry -> matches(entry, context));
    }

    private static boolean matches(final CreationRestrictionConfig entry, final Context context) {

        return matchesResourceType(entry.getResourceTypes(), context)
                && matchesAuthSubjectPattern(entry.getAuthSubject(), context)
                && matchesNamespacePattern(entry.getNamespace(), context);

    }

    private static boolean matchesResourceType(final Set<String> resourceTypes, final Context context) {
        if (resourceTypes.isEmpty()) {
            // no restriction -> pass
            LOG.withCorrelationId(context.headers()).debug("No resource type restriction: pass");
            return true;
        }

        if (resourceTypes.contains(context.resourceType())) {
            return true;
        }

        LOG.withCorrelationId(context.headers()).debug("No resource type match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    private static boolean matchesAuthSubjectPattern(final List<Pattern> authSubjectPatterns, final Context context) {
        if (authSubjectPatterns.isEmpty()) {
            // no restrictions -> pass
            LOG.withCorrelationId(context.headers()).debug("No auth subject restriction: pass");
            return true;
        }

        for (final var subject : context.headers().getAuthorizationContext().getAuthorizationSubjectIds()) {
            for (final var authSubjectPattern : authSubjectPatterns) {
                if (authSubjectPattern.matcher(subject).matches()) {
                    LOG.withCorrelationId(context.headers()).debug("Matched auth subject {}: pass", subject);
                    // entry found -> pass
                    return true;
                }
            }
        }

        LOG.withCorrelationId(context.headers()).debug("No auth subject match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    private static boolean matchesNamespacePattern(final List<Pattern> namespacePatterns, final Context context) {

        if (namespacePatterns.isEmpty()) {
            LOG.withCorrelationId(context.headers()).debug("No namespace restriction: pass");
            // no restrictions -> pass
            return true;
        }

        var namespace = context.namespace();
        for (final var namespacePattern : namespacePatterns) {
            if (namespacePattern.matcher(namespace).matches()) {
                LOG.withCorrelationId(context.headers()).debug("Namespace '{}' matched {}: pass", namespace,
                        namespacePattern);
                return true;
            }
        }

        LOG.withCorrelationId(context.headers()).debug("No namespace match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                ']';
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        final CompletionStage<Signal<?>> result;

        if (isCreatingCommand(signal)) {
            result = CompletableFuture.completedStage(handleCreatingCommand(signal));
        } else {
            result = CompletableFuture.completedFuture(signal);
        }
        return result;
    }

    private static boolean isCreatingCommand(final Signal<?> signal) {
        return signal instanceof Command<?> command && command.getCategory().equals(Command.Category.CREATE);
    }

    private Signal<?> handleCreatingCommand(final Signal<?> signal) {

        final WithEntityId withEntityId = getMessageAsWithEntityId(signal);
        final NamespacedEntityId entityId = getEntityIdAsNamespacedEntityId(withEntityId.getEntityId());
        final var context = new Context(signal.getResourceType(), entityId.getNamespace(),
                signal.getDittoHeaders());
        if (canCreate(context)) {
            return signal;
        } else {
            throw EntityNotCreatableException.newBuilder(withEntityId.getEntityId())
                    .dittoHeaders(signal.getDittoHeaders())
                    .build();
        }
    }

    /**
     * The context for evaluating if an entity may be created or not.
     */
    record Context(String resourceType, String namespace, DittoHeaders headers) {}

}
