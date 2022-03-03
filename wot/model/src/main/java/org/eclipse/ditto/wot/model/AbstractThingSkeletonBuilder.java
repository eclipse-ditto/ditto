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
package org.eclipse.ditto.wot.model;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation of {@link ThingSkeletonBuilder}.
 */
abstract class AbstractThingSkeletonBuilder<B extends ThingSkeletonBuilder<B, T>, T extends ThingSkeleton<T>>
        extends AbstractTypedJsonObjectBuilder<B, T> implements ThingSkeletonBuilder<B, T> {

    protected AbstractThingSkeletonBuilder(final JsonObjectBuilder wrappedObjectBuilder, final Class<?> selfType) {
        super(wrappedObjectBuilder, selfType);
    }

    @Override
    public B setAtContext(final AtContext atContext) {
        if (atContext instanceof SingleAtContext) {
            putValue(ThingSkeleton.JsonFields.AT_CONTEXT, atContext.toString());
        } else if (atContext instanceof MultipleAtContext) {
            final MultipleAtContext multipleContext = (MultipleAtContext) atContext;
            putValue(ThingSkeleton.JsonFields.AT_CONTEXT_MULTIPLE, multipleContext.toJson());
        } else {
            throw new IllegalArgumentException("Unsupported @context: " + atContext.getClass().getSimpleName());
        }
        return myself;
    }

    @Override
    public B setAtType(@Nullable final AtType atType) {
        if (atType != null) {
            if (atType instanceof SingleAtType) {
                putValue(ThingSkeleton.JsonFields.AT_TYPE, atType.toString());
            } else if (atType instanceof MultipleAtType) {
                final MultipleAtType multipleType = (MultipleAtType) atType;
                final JsonArray collectedSingles = StreamSupport.stream(multipleType.spliterator(), false)
                        .map(CharSequence::toString)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray());
                putValue(ThingSkeleton.JsonFields.AT_TYPE_MULTIPLE, collectedSingles);
            } else {
                throw new IllegalArgumentException("Unsupported @type: " + atType.getClass().getSimpleName());
            }
        } else {
            remove(ThingSkeleton.JsonFields.AT_TYPE);
        }
        return myself;
    }

    @Override
    public B setId(@Nullable final IRI id) {
        if (id != null) {
            putValue(ThingSkeleton.JsonFields.ID, id.toString());
        } else {
            remove(ThingSkeleton.JsonFields.ID);
        }
        return myself;
    }

    @Override
    public B setTitle(@Nullable final Title title) {
        if (title != null) {
            putValue(ThingSkeleton.JsonFields.TITLE, title.toString());
        } else {
            remove(ThingSkeleton.JsonFields.TITLE);
        }
        return myself;
    }

    @Override
    public B setTitles(@Nullable final Titles titles) {
        if (titles != null) {
            putValue(ThingSkeleton.JsonFields.TITLES, titles.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.TITLES);
        }
        return myself;
    }

    @Override
    public B setDescription(@Nullable final Description description) {
        if (description != null) {
            putValue(ThingSkeleton.JsonFields.DESCRIPTION, description.toString());
        } else {
            remove(ThingSkeleton.JsonFields.DESCRIPTION);
        }
        return myself;
    }

    @Override
    public B setDescriptions(@Nullable final Descriptions descriptions) {
        if (descriptions != null) {
            putValue(ThingSkeleton.JsonFields.DESCRIPTIONS, descriptions.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.DESCRIPTIONS);
        }
        return myself;
    }

    @Override
    public B setVersion(@Nullable final Version version) {
        if (version != null) {
            putValue(ThingSkeleton.JsonFields.VERSION, version.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.VERSION);
        }
        return myself;
    }

    @Override
    public B setBase(@Nullable final IRI base) {
        if (base != null) {
            putValue(ThingSkeleton.JsonFields.BASE, base.toString());
        } else {
            remove(ThingSkeleton.JsonFields.BASE);
        }
        return myself;
    }

    @Override
    public B setLinks(final Collection<BaseLink<?>> links) {
        return setLinks(Links.of(links));
    }

    @Override
    public B setLinks(@Nullable final Links links) {
        if (links != null) {
            putValue(ThingSkeleton.JsonFields.LINKS, links.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.LINKS);
        }
        return myself;
    }

    @Override
    public B setProperties(@Nullable final Properties properties) {
        if (properties != null) {
            putValue(ThingSkeleton.JsonFields.PROPERTIES, properties.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.PROPERTIES);
        }
        return myself;
    }

    @Override
    public B setActions(@Nullable final Actions actions) {
        if (actions != null) {
            putValue(ThingSkeleton.JsonFields.ACTIONS, actions.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.ACTIONS);
        }
        return myself;
    }

    @Override
    public B setEvents(@Nullable final Events events) {
        if (events != null) {
            putValue(ThingSkeleton.JsonFields.EVENTS, events.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.EVENTS);
        }
        return myself;
    }

    @Override
    public B setForms(final Collection<RootFormElement> forms) {
        return setForms(RootForms.of(forms));
    }

    @Override
    public B setForms(@Nullable final RootForms forms) {
        if (forms != null) {
            putValue(ThingSkeleton.JsonFields.FORMS, forms.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.FORMS);
        }
        return myself;
    }

    @Override
    public B setSecurityDefinitions(@Nullable final SecurityDefinitions securityDefinitions) {
        if (securityDefinitions != null) {
            putValue(ThingSkeleton.JsonFields.SECURITY_DEFINITIONS, securityDefinitions.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.SECURITY_DEFINITIONS);
        }
        return myself;
    }

    @Override
    public B setSchemaDefinitions(@Nullable final SchemaDefinitions schemaDefinitions) {
        if (schemaDefinitions != null) {
            putValue(ThingSkeleton.JsonFields.SCHEMA_DEFINITIONS, schemaDefinitions.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.SCHEMA_DEFINITIONS);
        }
        return myself;
    }

    @Override
    public B setUriVariables(@Nullable final UriVariables uriVariables) {
        if (uriVariables != null) {
            putValue(ThingSkeleton.JsonFields.URI_VARIABLES, uriVariables.toJson());
        } else {
            remove(ThingSkeleton.JsonFields.URI_VARIABLES);
        }
        return myself;
    }

    @Override
    public B setSupport(@Nullable final IRI support) {
        if (support != null) {
            putValue(ThingSkeleton.JsonFields.SUPPORT, support.toString());
        } else {
            remove(ThingSkeleton.JsonFields.SUPPORT);
        }
        return myself;
    }

    @Override
    public B setCreated(@Nullable final Instant created) {
        if (created != null) {
            putValue(ThingSkeleton.JsonFields.CREATED, created.toString());
        } else {
            remove(ThingSkeleton.JsonFields.CREATED);
        }
        return myself;
    }

    @Override
    public B setModified(@Nullable final Instant modified) {
        if (modified != null) {
            putValue(ThingSkeleton.JsonFields.MODIFIED, modified.toString());
        } else {
            remove(ThingSkeleton.JsonFields.MODIFIED);
        }
        return myself;
    }

    @Override
    public B setSecurity(@Nullable final Security security) {
        if (security != null) {
            if (security instanceof SingleSecurity) {
                putValue(ThingSkeleton.JsonFields.SECURITY, security.toString());
            } else if (security instanceof MultipleSecurity) {
                final MultipleSecurity multipleSecurity = (MultipleSecurity) security;
                final JsonArray collectedSingles = StreamSupport.stream(multipleSecurity.spliterator(), false)
                        .map(CharSequence::toString)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray());
                putValue(ThingSkeleton.JsonFields.SECURITY_MULTIPLE, collectedSingles);
            } else {
                throw new IllegalArgumentException("Unsupported security: " + security.getClass().getSimpleName());
            }
        } else {
            remove(ThingSkeleton.JsonFields.SECURITY);
        }
        return myself;
    }

    @Override
    public B setProfile(@Nullable final Profile profile) {
        if (profile != null) {
            if (profile instanceof SingleProfile) {
                putValue(ThingSkeleton.JsonFields.PROFILE, profile.toString());
            } else if (profile instanceof MultipleProfile) {
                final MultipleProfile multipleProfile = (MultipleProfile) profile;
                final JsonArray collectedSingles = StreamSupport.stream(multipleProfile.spliterator(), false)
                        .map(CharSequence::toString)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray());
                putValue(ThingSkeleton.JsonFields.PROFILE_MULTIPLE, collectedSingles);
            } else {
                throw new IllegalArgumentException("Unsupported profile: " + profile.getClass().getSimpleName());
            }
        } else {
            remove(ThingSkeleton.JsonFields.PROFILE);
        }
        return myself;
    }

}
