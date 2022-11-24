/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import java.util.Enumeration;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.qpid.jms.JmsAcknowledgeCallback;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;

/**
 * Fake JMS message to defeat the Qpid client when it tries to set AMQP properties willy-nilly.
 * Override all the setters to do nothing if already set.
 */
public final class JMSMessageWorkaround extends JmsMessage {

    private final JmsMessage message;

    private JMSMessageWorkaround(final JmsMessage message) {
        super(message.getFacade());
        // ignore everything; delegate all public methods to message.
        this.message = message;
        // copy message anyway because "getBody" is final (why?)
        copy(message);
    }

    /**
     * Wrap a JMS message in this class. The wrapped JMS message should not be used anywhere else afterwards.
     *
     * @param jmsMessage the JMS message.
     * @return the wrapper which takes over its identity.
     */
    public static JmsMessage wrap(final JmsMessage jmsMessage) {
        return new JMSMessageWorkaround(jmsMessage);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + message.toString() + "]";
    }

    @Override
    public JmsMessage copy() throws JMSException {
        return new JMSMessageWorkaround(message.copy());
    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        return message.equals(o);
    }

    @Override
    public void acknowledge() throws JMSException {
        message.acknowledge();
    }

    @Override
    public String getJMSMessageID() throws JMSException {
        return message.getJMSMessageID();
    }

    @Override
    public void setJMSMessageID(final String id) throws JMSException {
        if (message.getJMSMessageID() == null) {
            message.setJMSMessageID(id);
        }
    }

    @Override
    public long getJMSTimestamp() throws JMSException {
        return message.getJMSTimestamp();
    }

    @Override
    public void setJMSTimestamp(final long timestamp) throws JMSException {
        if (message.getJMSTimestamp() == 0L) {
            message.setJMSTimestamp(timestamp);
        }
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        return message.getJMSCorrelationIDAsBytes();
    }

    @Override
    public void setJMSCorrelationIDAsBytes(final byte[] correlationID) throws JMSException {
        if (message.getJMSCorrelationID() == null) {
            message.setJMSCorrelationIDAsBytes(correlationID);
        }
    }

    @Override
    public String getJMSCorrelationID() throws JMSException {
        return message.getJMSCorrelationID();
    }

    @Override
    public void setJMSCorrelationID(final String correlationID) throws JMSException {
        if (message.getJMSCorrelationID() == null) {
            message.setJMSCorrelationID(correlationID);
        }
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException {
        return message.getJMSReplyTo();
    }

    @Override
    public void setJMSReplyTo(final Destination replyTo) throws JMSException {
        if (message.getJMSReplyTo() == null) {
            message.setJMSReplyTo(replyTo);
        }
    }

    @Override
    public Destination getJMSDestination() throws JMSException {
        return message.getJMSDestination();
    }

    @Override
    public void setJMSDestination(final Destination destination) throws JMSException {
        if (message.getJMSDestination() == null) {
            message.setJMSDestination(destination);
        }
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException {
        return message.getJMSDeliveryMode();
    }

    @Override
    public void setJMSDeliveryMode(final int deliveryMode) throws JMSException {
        // not settable as AMQP property
        message.setJMSDeliveryMode(deliveryMode);
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException {
        return message.getJMSRedelivered();
    }

    @Override
    public void setJMSRedelivered(final boolean redelivered) throws JMSException {
        // not settable as AMQP property
        message.setJMSRedelivered(redelivered);
    }

    @Override
    public String getJMSType() throws JMSException {
        return message.getJMSType();
    }

    @Override
    public void setJMSType(final String type) throws JMSException {
        if (message.getJMSType() == null) {
            message.setJMSType(type);
        }
    }

    @Override
    public long getJMSExpiration() throws JMSException {
        return message.getJMSExpiration();
    }

    @Override
    public void setJMSExpiration(final long expiration) throws JMSException {
        if (message.getJMSExpiration() == 0L) {
            message.setJMSExpiration(expiration);
        }
    }

    @Override
    public int getJMSPriority() throws JMSException {
        return message.getJMSPriority();
    }

    @Override
    public void setJMSPriority(final int priority) throws JMSException {
        // not settable as AMQP property
        message.setJMSPriority(priority);
    }

    @Override
    public long getJMSDeliveryTime() throws JMSException {
        return message.getJMSDeliveryTime();
    }

    @Override
    public void setJMSDeliveryTime(final long deliveryTime) throws JMSException {
        // not settable as AMQP property
        message.setJMSDeliveryTime(deliveryTime);
    }

    @Override
    public void clearProperties() throws JMSException {
        message.clearProperties();
    }

    @Override
    public boolean isBodyAssignableTo(final Class target) throws JMSException {
        return message.isBodyAssignableTo(target);
    }

    @Override
    protected <T> T doGetBody(final Class<T> asType) throws JMSException {
        return message.getBody(asType);
    }

    @Override
    public void clearBody() throws JMSException {
        message.clearBody();
    }

    @Override
    public boolean isValidatePropertyNames() {
        return message.isValidatePropertyNames();
    }

    @Override
    public void setValidatePropertyNames(final boolean validatePropertyNames) {
        message.setValidatePropertyNames(validatePropertyNames);
    }

    @Override
    public boolean isReadOnly() {
        return message.isReadOnly();
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        message.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnlyBody() {
        return message.isReadOnlyBody();
    }

    @Override
    public void setReadOnlyBody(final boolean readOnlyBody) {
        message.setReadOnlyBody(readOnlyBody);
    }

    @Override
    public boolean isReadOnlyProperties() {
        return message.isReadOnlyProperties();
    }

    @Override
    public void setReadOnlyProperties(final boolean readOnlyProperties) {
        message.setReadOnlyProperties(readOnlyProperties);
    }

    @Override
    public boolean propertyExists(final String name) throws JMSException {
        return message.propertyExists(name);
    }

    @Override
    public Enumeration<?> getPropertyNames() throws JMSException {
        return message.getPropertyNames();
    }

    @Override
    public Enumeration<?> getAllPropertyNames() throws JMSException {
        return message.getAllPropertyNames();
    }

    @Override
    public void setObjectProperty(final String name, final Object value) throws JMSException {
        message.setObjectProperty(name, value);
    }

    @Override
    public Object getObjectProperty(final String name) throws JMSException {
        return message.getObjectProperty(name);
    }

    @Override
    public boolean getBooleanProperty(final String name) throws JMSException {
        return message.getBooleanProperty(name);
    }

    @Override
    public byte getByteProperty(final String name) throws JMSException {
        return message.getByteProperty(name);
    }

    @Override
    public short getShortProperty(final String name) throws JMSException {
        return message.getShortProperty(name);
    }

    @Override
    public int getIntProperty(final String name) throws JMSException {
        return message.getIntProperty(name);
    }

    @Override
    public long getLongProperty(final String name) throws JMSException {
        return message.getLongProperty(name);
    }

    @Override
    public float getFloatProperty(final String name) throws JMSException {
        return message.getFloatProperty(name);
    }

    @Override
    public double getDoubleProperty(final String name) throws JMSException {
        return message.getDoubleProperty(name);
    }

    @Override
    public String getStringProperty(final String name) throws JMSException {
        return message.getStringProperty(name);
    }

    @Override
    public void setBooleanProperty(final String name, final boolean value) throws JMSException {
        message.setBooleanProperty(name, value);
    }

    @Override
    public void setByteProperty(final String name, final byte value) throws JMSException {
        message.setByteProperty(name, value);
    }

    @Override
    public void setShortProperty(final String name, final short value) throws JMSException {
        message.setShortProperty(name, value);
    }

    @Override
    public void setIntProperty(final String name, final int value) throws JMSException {
        message.setIntProperty(name, value);
    }

    @Override
    public void setLongProperty(final String name, final long value) throws JMSException {
        message.setLongProperty(name, value);
    }

    @Override
    public void setFloatProperty(final String name, final float value) throws JMSException {
        message.setFloatProperty(name, value);
    }

    @Override
    public void setDoubleProperty(final String name, final double value) throws JMSException {
        message.setDoubleProperty(name, value);
    }

    @Override
    public void setStringProperty(final String name, final String value) throws JMSException {
        message.setStringProperty(name, value);
    }

    @Override
    public JmsAcknowledgeCallback getAcknowledgeCallback() {
        return message.getAcknowledgeCallback();
    }

    @Override
    public void setAcknowledgeCallback(final JmsAcknowledgeCallback jmsAcknowledgeCallback) {
        message.setAcknowledgeCallback(jmsAcknowledgeCallback);
    }

    @Override
    public void onSend(final long producerTtl) throws JMSException {
        message.onSend(producerTtl);
    }

    @Override
    public void onSendComplete() {
        message.onSendComplete();
    }

    @Override
    public void onDispatch() throws JMSException {
        message.onDispatch();
    }

    @Override
    public JmsConnection getConnection() {
        return message.getConnection();
    }

    @Override
    public void setConnection(final JmsConnection connection) {
        message.setConnection(connection);
    }

    @Override
    public JmsMessageFacade getFacade() {
        return message.getFacade();
    }

    @Override
    public boolean isExpired() {
        return message.isExpired();
    }

}
