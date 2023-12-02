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

/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Utils from '../utils.js';
import featureMessagesHTML from './featureMessages.html';
import * as Features from './features.js';
import * as Things from './things.js';

let theFeatureId;

const dom = {
  inputMessageSubject: null,
  inputMessageTimeout: null,
  inputMessageTemplate: null,
  buttonMessageSend: null,
  buttonMessageFavorite: null,
  ulMessageTemplates: null,
  favIconMessage: null,
  tableValidationFeature: null,
};

let acePayload;
let aceResponse;

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Environments.addChangeListener(onEnvironmentChanged);
  Features.addChangeListener(onFeatureChanged);

  Utils.addTab(
      document.getElementById('tabItemsFeatures'),
      document.getElementById('tabContentFeatures'),
      'Send Message',
      featureMessagesHTML,
  );

  Utils.getAllElementsById(dom);

  acePayload = Utils.createAceEditor('acePayload', 'ace/mode/json');
  aceResponse = Utils.createAceEditor('aceResponse', 'ace/mode/json', true);


  dom.buttonMessageSend.onclick = () => {
    Utils.assert(theFeatureId, 'Please select a Feature', dom.tableValidationFeature);
    Utils.assert(dom.inputMessageSubject.value, 'Please give a Subject', dom.inputMessageSubject);
    Utils.assert(dom.inputMessageTimeout.value, 'Please give a timeout', dom.inputMessageTimeout);
    dom.buttonMessageSend.classList.add('busy');
    dom.buttonMessageSend.disabled = true;
    messageFeature();
  };

  dom.buttonMessageFavorite.onclick = () => {
    const templateName = dom.inputMessageTemplate.value;
    const featureId = theFeatureId;
    const payload = acePayload.getValue();
    Utils.assert(featureId, 'Please select a Feature', dom.tableValidationFeature);
    Utils.assert(templateName, 'Please give a name for the template', dom.inputMessageTemplate);
    Environments.current().messageTemplates[featureId] = Environments.current().messageTemplates[featureId] || {};
    if (Object.keys(Environments.current().messageTemplates[featureId]).includes(templateName) &&
        dom.favIconMessage.classList.contains('bi-star-fill')) {
      dom.favIconMessage.classList.replace('bi-star-fill', 'bi-star');
      delete Environments.current().messageTemplates[featureId][templateName];
    } else {
      dom.favIconMessage.classList.replace('bi-star', 'bi-star-fill');
      Environments.current().messageTemplates[featureId][templateName] = {
        subject: dom.inputMessageSubject.value,
        timeout: dom.inputMessageTimeout.value,
        ...(payload) && {payload: JSON.parse(payload)},
      };
      acePayload.session.getUndoManager().markClean();
    }
    Environments.environmentsJsonChanged('messageTemplates');
  };

  dom.ulMessageTemplates.addEventListener('click', (event) => {
    if (event.target && event.target.classList.contains('dropdown-item')) {
      dom.favIconMessage.classList.replace('bi-star', 'bi-star-fill');
      const template = Environments.current().messageTemplates[theFeatureId][event.target.textContent];
      dom.inputMessageTemplate.value = event.target.textContent;
      dom.inputMessageSubject.value = template.subject;
      dom.inputMessageTimeout.value = template.timeout;
      acePayload.setValue(Utils.stringifyPretty(template.payload), -1);
      acePayload.session.getUndoManager().markClean();
    }
  });

  [dom.inputMessageTemplate, dom.inputMessageSubject, dom.inputMessageTimeout].forEach((e) => {
    e.addEventListener('change', () => {
      dom.favIconMessage.classList.replace('bi-star-fill', 'bi-star');
    });
  });

  acePayload.on('input', () => {
    if (!acePayload.session.getUndoManager().isClean()) {
      dom.favIconMessage.classList.replace('bi-star-fill', 'bi-star');
    }
  });
}

/**
 * Calls Ditto to send a message with the parameters of the fields in the UI
 */
function messageFeature() {
  let payload: any;
  if (acePayload && acePayload.getValue().length > 0) {
    payload = JSON.parse(acePayload.getValue());
  } else {
    payload = null;
  }
  aceResponse.setValue('');
  API.callDittoREST('POST', '/things/' + Things.theThing.thingId +
    '/features/' + theFeatureId +
    '/inbox/messages/' + dom.inputMessageSubject.value +
    '?timeout=' + dom.inputMessageTimeout.value,
    payload,
    null,
    false,
    false,
    true
  ).then((data) => {
    dom.buttonMessageSend.classList.remove('busy');
    dom.buttonMessageSend.disabled = false;
    if (dom.inputMessageTimeout.value > 0) {
      aceResponse.setValue(Utils.stringifyPretty(data), -1);
    }
  }).catch((err) => {
    dom.buttonMessageSend.classList.remove('busy');
    dom.buttonMessageSend.disabled = false;
    aceResponse.setValue(`Error: ${err}`);
  });
}

function onEnvironmentChanged(modifiedField) {
  Environments.current()['messageTemplates'] = Environments.current()['messageTemplates'] || {};

  if (!modifiedField) {
    clearAllFields();
  }
  if (modifiedField === 'messageTemplates') {
    refillTemplates();
  }
}

function clearAllFields() {
  dom.favIconMessage.classList.replace('bi-star-fill', 'bi-star');
  dom.inputMessageTemplate.value = null;
  dom.inputMessageSubject.value = null;
  dom.inputMessageTimeout.value = '10';
  acePayload.setValue('');
  aceResponse.setValue('');
  dom.ulMessageTemplates.innerHTML = '';
  dom.buttonMessageSend.disabled = !theFeatureId || theFeatureId === '';
}

function refillTemplates() {
  dom.ulMessageTemplates.innerHTML = '';
  Utils.addDropDownEntries(dom.ulMessageTemplates, ['Saved message templates'], true);
  if (theFeatureId && Environments.current().messageTemplates[theFeatureId]) {
    Utils.addDropDownEntries(
        dom.ulMessageTemplates,
        Object.keys(Environments.current().messageTemplates[theFeatureId]),
    );
  }
}

function onFeatureChanged(featureId) {
  if (featureId !== theFeatureId) {
    theFeatureId = featureId;
    clearAllFields();
    refillTemplates();
  }
}

