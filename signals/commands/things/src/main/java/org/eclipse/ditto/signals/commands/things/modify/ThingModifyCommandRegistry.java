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
package org.eclipse.ditto.signals.commands.things.modify;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link ThingModifyCommand}s.
 */
@Immutable
public final class ThingModifyCommandRegistry extends AbstractCommandRegistry<ThingModifyCommand> {

    private ThingModifyCommandRegistry(final Map<String, JsonParsable<ThingModifyCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ThingModifyCommandRegistry}.
     *
     * @return the command registry.
     */
    public static ThingModifyCommandRegistry newInstance() {
        final Map<String, JsonParsable<ThingModifyCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(CreateThing.TYPE, CreateThing::fromJson);
        parseStrategies.put(ModifyThing.TYPE, ModifyThing::fromJson);
        parseStrategies.put(DeleteThing.TYPE, DeleteThing::fromJson);

        parseStrategies.put(ModifyAcl.TYPE, ModifyAcl::fromJson);
        parseStrategies.put(ModifyAclEntry.TYPE, ModifyAclEntry::fromJson);
        parseStrategies.put(DeleteAclEntry.TYPE, DeleteAclEntry::fromJson);

        parseStrategies.put(ModifyPolicyId.TYPE, ModifyPolicyId::fromJson);

        parseStrategies.put(ModifyAttributes.TYPE, ModifyAttributes::fromJson);
        parseStrategies.put(DeleteAttributes.TYPE, DeleteAttributes::fromJson);
        parseStrategies.put(ModifyAttribute.TYPE, ModifyAttribute::fromJson);
        parseStrategies.put(DeleteAttribute.TYPE, DeleteAttribute::fromJson);

        parseStrategies.put(ModifyFeatures.TYPE, ModifyFeatures::fromJson);
        parseStrategies.put(DeleteFeatures.TYPE, DeleteFeatures::fromJson);
        parseStrategies.put(ModifyFeature.TYPE, ModifyFeature::fromJson);
        parseStrategies.put(DeleteFeature.TYPE, DeleteFeature::fromJson);

        parseStrategies.put(ModifyFeatureDefinition.TYPE, ModifyFeatureDefinition::fromJson);
        parseStrategies.put(DeleteFeatureDefinition.TYPE, DeleteFeatureDefinition::fromJson);

        parseStrategies.put(ModifyFeatureProperties.TYPE, ModifyFeatureProperties::fromJson);
        parseStrategies.put(DeleteFeatureProperties.TYPE, DeleteFeatureProperties::fromJson);
        parseStrategies.put(ModifyFeatureProperty.TYPE, ModifyFeatureProperty::fromJson);
        parseStrategies.put(DeleteFeatureProperty.TYPE, DeleteFeatureProperty::fromJson);

        return new ThingModifyCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return ThingCommand.TYPE_PREFIX;
    }

}
