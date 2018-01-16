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
package org.eclipse.ditto.signals.commands.things.query;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingQueryCommand}s.
 */
@Immutable
public final class ThingQueryCommandRegistry extends AbstractCommandRegistry<ThingQueryCommand> {

    private ThingQueryCommandRegistry(final Map<String, JsonParsable<ThingQueryCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ThingQueryCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ThingQueryCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingQueryCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(RetrieveThing.TYPE, RetrieveThing::fromJson);
        parseStrategies.put(RetrieveThings.TYPE, RetrieveThings::fromJson);

        parseStrategies.put(RetrieveAcl.TYPE, RetrieveAcl::fromJson);
        parseStrategies.put(RetrieveAclEntry.TYPE, RetrieveAclEntry::fromJson);

        parseStrategies.put(RetrievePolicyId.TYPE, RetrievePolicyId::fromJson);

        parseStrategies.put(RetrieveAttributes.TYPE, RetrieveAttributes::fromJson);
        parseStrategies.put(RetrieveAttribute.TYPE, RetrieveAttribute::fromJson);

        parseStrategies.put(RetrieveFeatures.TYPE, RetrieveFeatures::fromJson);
        parseStrategies.put(RetrieveFeature.TYPE, RetrieveFeature::fromJson);

        parseStrategies.put(RetrieveFeatureDefinition.TYPE, RetrieveFeatureDefinition::fromJson);
        parseStrategies.put(RetrieveFeatureProperties.TYPE, RetrieveFeatureProperties::fromJson);
        parseStrategies.put(RetrieveFeatureProperty.TYPE, RetrieveFeatureProperty::fromJson);

        return new ThingQueryCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingCommand.TYPE_PREFIX;
    }

}
