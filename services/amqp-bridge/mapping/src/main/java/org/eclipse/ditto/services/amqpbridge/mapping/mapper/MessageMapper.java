package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.InternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

public interface MessageMapper {

    Optional<String> getContentType();

    void configure(final MessageMapperConfiguration configuration);

    @Nullable
    Adaptable map(@Nullable final InternalMessage message);

    @Nullable
    InternalMessage map(@Nullable final Adaptable adaptable);
}
