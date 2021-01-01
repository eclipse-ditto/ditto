/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;

import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.policies.common.config.PolicyConfig;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyActionCommand;

/**
 * Abstract base class for {@link org.eclipse.ditto.signals.commands.policies.PolicyCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.services.models.policies.commands.sudo.SudoCommand} are handled which are no PolicyCommands.
 */
abstract class AbstractPolicyActionCommandStrategy<C extends Command<C>> extends AbstractPolicyCommandStrategy<C> {

    private static final String RESOLVE_SUBJECT_ID = "resolveSubjectId";

    private final Method subjectIdResolver;

    AbstractPolicyActionCommandStrategy(final Class<C> theMatchingClass,
            final PolicyConfig policyConfig) {
        super(theMatchingClass, policyConfig);
        try {
            final Class<?> subjectIdResolverClass = Class.forName(policyConfig.getSubjectIdResolver());
            subjectIdResolver =
                    subjectIdResolverClass.getMethod(RESOLVE_SUBJECT_ID, PolicyEntry.class, PolicyActionCommand.class);
        } catch (final ClassNotFoundException | NoSuchMethodException | SecurityException e) {
            throw new CompletionException(e);
        }
    }

    SubjectId resolveSubjectId(final PolicyEntry policyEntry, final PolicyActionCommand<?> policyActionCommand) {
        try {
            return (SubjectId) subjectIdResolver.invoke(null, policyEntry, policyActionCommand);
        } catch (final InvocationTargetException | IllegalAccessException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw new CompletionException(e);
            }
        }
    }
}
