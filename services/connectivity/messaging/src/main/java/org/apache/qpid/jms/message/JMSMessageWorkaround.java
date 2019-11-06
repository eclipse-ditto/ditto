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
package org.apache.qpid.jms.message;

import java.util.Enumeration;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.qpid.jms.JmsAcknowledgeCallback;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.message.facade.JmsMessageFacade;

/**
 * Fake JMS message to defeat the Qpid client when it tries to set AMQP properties willy-nilly.
 * Override all the setters to do nothing of already set.
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
    public JmsMessage copy() throws JMSException {
        return new JMSMessageWorkaround(message.copy());
    }

    @Override
    public int hashCode() {
        return message.hashCode();
    }

    @Override
    public boolean equals(Object o) {
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
        if (getJMSCorrelationID() == null) {
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
        if (getJMSReplyTo() == null) {
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
        return message.doGetBody(asType);
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
    public void setValidatePropertyNames(boolean validatePropertyNames) {
        message.validatePropertyNames = validatePropertyNames;
    }

    @Override
    public boolean isReadOnly() {
        return message.isReadOnly();
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        message.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnlyBody() {
        return message.isReadOnlyBody();
    }

    @Override
    public void setReadOnlyBody(boolean readOnlyBody) {
        message.setReadOnlyBody(readOnlyBody);
    }

    @Override
    public boolean isReadOnlyProperties() {
        return message.isReadOnlyProperties();
    }

    @Override
    public void setReadOnlyProperties(boolean readOnlyProperties) {
        message.setReadOnlyProperties(readOnlyProperties);
    }

    @Override
    public boolean propertyExists(String name) throws JMSException {
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
    public void setObjectProperty(String name, Object value) throws JMSException {
        message.setObjectProperty(name, value);
    }

    @Override
    public Object getObjectProperty(String name) throws JMSException {
        return message.getObjectProperty(name);
    }

    @Override
    public boolean getBooleanProperty(String name) throws JMSException {
        return message.getBooleanProperty(name);
    }

    @Override
    public byte getByteProperty(String name) throws JMSException {
        return message.getByteProperty(name);
    }

    @Override
    public short getShortProperty(String name) throws JMSException {
        return message.getShortProperty(name);
    }

    @Override
    public int getIntProperty(String name) throws JMSException {
        return message.getIntProperty(name);
    }

    @Override
    public long getLongProperty(String name) throws JMSException {
        return message.getLongProperty(name);
    }

    @Override
    public float getFloatProperty(String name) throws JMSException {
        return message.getFloatProperty(name);
    }

    @Override
    public double getDoubleProperty(String name) throws JMSException {
        return message.getDoubleProperty(name);
    }

    @Override
    public String getStringProperty(String name) throws JMSException {
        return message.getStringProperty(name);
    }

    @Override
    public void setBooleanProperty(String name, boolean value) throws JMSException {
        message.setBooleanProperty(name, value);
    }

    @Override
    public void setByteProperty(String name, byte value) throws JMSException {
        message.setByteProperty(name, value);
    }

    @Override
    public void setShortProperty(String name, short value) throws JMSException {
        message.setShortProperty(name, value);
    }

    @Override
    public void setIntProperty(String name, int value) throws JMSException {
        message.setIntProperty(name, value);
    }

    @Override
    public void setLongProperty(String name, long value) throws JMSException {
        message.setLongProperty(name, value);
    }

    @Override
    public void setFloatProperty(String name, float value) throws JMSException {
        message.setFloatProperty(name, value);
    }

    @Override
    public void setDoubleProperty(String name, double value) throws JMSException {
        message.setDoubleProperty(name, value);
    }

    @Override
    public void setStringProperty(String name, String value) throws JMSException {
        message.setStringProperty(name, value);
    }

    @Override
    public JmsAcknowledgeCallback getAcknowledgeCallback() {
        return message.getAcknowledgeCallback();
    }

    @Override
    public void setAcknowledgeCallback(JmsAcknowledgeCallback jmsAcknowledgeCallback) {
        message.setAcknowledgeCallback(jmsAcknowledgeCallback);
    }

    @Override
    public void onSend(long producerTtl) throws JMSException {
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
    public void setConnection(JmsConnection connection) {
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
