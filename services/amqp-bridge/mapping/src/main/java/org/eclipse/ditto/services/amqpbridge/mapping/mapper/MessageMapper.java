package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.ExternalMessage;
import org.eclipse.ditto.protocoladapter.Adaptable;

public interface MessageMapper {

    Optional<String> getContentType();

    void configure(MessageMapperConfiguration configuration);

    Adaptable map(ExternalMessage message);

    ExternalMessage map(Adaptable adaptable);
}
