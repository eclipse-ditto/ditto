/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import messagesHTML from './thingMessages.html';
import * as Things from './things.js';

const dom = {
  inputThingMessageSubject: null,
  inputThingMessageTimeout: null,
  inputThingMessageTemplate: null,
  buttonThingMessageSend: null,
  buttonThingMessageFavorite: null,
  ulThingMessageTemplates: null,
  favIconThingMessage: null,
};

let acePayload;
let aceResponse;

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);
  Things.addChangeListener(onThingChanged);

  Utils.addTab(
      document.getElementById('tabItemsThing'),
      document.getElementById('tabContentThing'),
      'Send Message',
      messagesHTML,
  );

  Utils.getAllElementsById(dom);

  acePayload = Utils.createAceEditor('acePayloadThingMessage', 'ace/mode/json');
  aceResponse = Utils.createAceEditor('aceResponseThingMessage', 'ace/mode/json', true);


  dom.buttonThingMessageSend.onclick = () => {
    Utils.assert(dom.inputThingMessageSubject.value, 'Please give a Subject', dom.inputThingMessageSubject);
    Utils.assert(dom.inputThingMessageTimeout.value, 'Please give a timeout', dom.inputThingMessageTimeout);
    dom.buttonThingMessageSend.classList.add('busy');
    dom.buttonThingMessageSend.disabled = true;
    messageThing();
  };

  dom.buttonThingMessageFavorite.onclick = () => {
    const templateName = dom.inputThingMessageTemplate.value;
    const payload = acePayload.getValue();
    Utils.assert(templateName, 'Please give a name for the template', dom.inputThingMessageTemplate);
    Environments.current().messageTemplates['/'] = Environments.current().messageTemplates['/'] || {};
    if (Object.keys(Environments.current().messageTemplates['/']).includes(templateName) &&
        dom.favIconThingMessage.classList.contains('bi-star-fill')) {
      dom.favIconThingMessage.classList.replace('bi-star-fill', 'bi-star');
      delete Environments.current().messageTemplates['/'][templateName];
    } else {
      dom.favIconThingMessage.classList.replace('bi-star', 'bi-star-fill');
      Environments.current().messageTemplates['/'][templateName] = {
        subject: dom.inputThingMessageSubject.value,
        timeout: dom.inputThingMessageTimeout.value,
        ...(payload) && {payload: JSON.parse(payload)},
      };
      acePayload.session.getUndoManager().markClean();
    }
    Environments.environmentsJsonChanged('messageTemplates');
  };

  dom.ulThingMessageTemplates.addEventListener('click', (event) => {
    if (event.target && event.target.classList.contains('dropdown-item')) {
      dom.favIconThingMessage.classList.replace('bi-star', 'bi-star-fill');
      const template = Environments.current().messageTemplates['/'][event.target.textContent];
      dom.inputThingMessageTemplate.value = event.target.textContent;
      dom.inputThingMessageSubject.value = template.subject;
      dom.inputThingMessageTimeout.value = template.timeout;
      acePayload.setValue(Utils.stringifyPretty(template.payload), -1);
      acePayload.session.getUndoManager().markClean();
    }
  });

  [dom.inputThingMessageTemplate, dom.inputThingMessageSubject, dom.inputThingMessageTimeout].forEach((e) => {
    e.addEventListener('change', () => {
      dom.favIconThingMessage.classList.replace('bi-star-fill', 'bi-star');
    });
  });

  acePayload.on('input', () => {
    if (!acePayload.session.getUndoManager().isClean()) {
      dom.favIconThingMessage.classList.replace('bi-star-fill', 'bi-star');
    }
  });
}

/**
 * Calls Ditto to send a message with the parameters of the fields in the UI
 */
function messageThing() {
  let payload: any;
  if (acePayload && acePayload.getValue().length > 0) {
    payload = JSON.parse(acePayload.getValue());
  } else {
    payload = null;
  }
  aceResponse.setValue('');
  API.callDittoREST('POST', '/things/' + Things.theThing.thingId +
    '/inbox/messages/' + dom.inputThingMessageSubject.value +
    '?timeout=' + dom.inputThingMessageTimeout.value,
    payload,
    null,
    false,
    false,
    true
  ).then((data) => {
    dom.buttonThingMessageSend.classList.remove('busy');
    dom.buttonThingMessageSend.disabled = false;
    if (dom.inputThingMessageTimeout.value > 0) {
      aceResponse.setValue(Utils.stringifyPretty(data), -1);
    }
  }).catch((err) => {
    dom.buttonThingMessageSend.classList.remove('busy');
    dom.buttonThingMessageSend.disabled = false;
    aceResponse.setValue(`Error: ${err}`);
  });
}

function onEnvironmentChanged(modifiedField) {
  Environments.current()['messageTemplates'] = Environments.current()['messageTemplates'] || {};

  if (!modifiedField) {
    clearAllFields();
  }
  refillTemplates();
}

function clearAllFields() {
  dom.favIconThingMessage.classList.replace('bi-star-fill', 'bi-star');
  dom.inputThingMessageTemplate.value = null;
  dom.inputThingMessageSubject.value = null;
  dom.inputThingMessageTimeout.value = '10';
  acePayload.setValue('');
  aceResponse.setValue('');
  dom.ulThingMessageTemplates.innerHTML = '';
  dom.buttonThingMessageSend.disabled = Things.theThing === null;
}

function refillTemplates() {
  dom.ulThingMessageTemplates.innerHTML = '';
  Utils.addDropDownEntries(dom.ulThingMessageTemplates, ['Saved message templates'], true);
  if (Environments.current().messageTemplates['/']) {
    Utils.addDropDownEntries(
        dom.ulThingMessageTemplates,
        Object.keys(Environments.current().messageTemplates['/']),
    );
  }
}

function onThingChanged(thing, isNewThing: boolean) {
  if (!thing || isNewThing) {
    clearAllFields();
  }
}
