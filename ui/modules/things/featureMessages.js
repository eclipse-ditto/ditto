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
import * as Things from './things.js';
import * as Features from './features.js';

const dom = {
  inputMessageSubject: null,
  inputMessageTimeout: null,
  inputMessageTemplate: null,
  buttonMessageSend: null,
  buttonMessageFavourite: null,
  ulMessageTemplates: null,
  favIconMessage: null,
  theFeatureId: null,
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
      'Message to Feature',
      await( await fetch('modules/things/featureMessages.html')).text(),
  );

  Utils.getAllElementsById(dom);

  acePayload = Utils.createAceEditor('acePayload', 'ace/mode/json');
  aceResponse = Utils.createAceEditor('aceResponse', 'ace/mode/json', true);


  dom.buttonMessageSend.onclick = () => {
    Utils.assert(dom.theFeatureId.value, 'Please select a Feature', dom.tableValidationFeature);
    Utils.assert(dom.inputMessageSubject.value, 'Please give a Subject', dom.inputMessageSubject);
    Utils.assert(dom.inputMessageTimeout.value, 'Please give a timeout', dom.inputMessageTimeout);
    messageFeature();
  };

  dom.buttonMessageFavourite.onclick = () => {
    const templateName = dom.inputMessageTemplate.value;
    const featureId = dom.theFeatureId.value;
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
        payload: JSON.parse(acePayload.getValue()),
      };
      acePayload.session.getUndoManager().markClean();
    }
    Environments.environmentsJsonChanged('messageTemplates');
  };

  dom.ulMessageTemplates.addEventListener('click', (event) => {
    if (event.target && event.target.classList.contains('dropdown-item')) {
      dom.favIconMessage.classList.replace('bi-star', 'bi-star-fill');
      const template = Environments.current().messageTemplates[dom.theFeatureId.value][event.target.textContent];
      dom.inputMessageTemplate.value = event.target.textContent;
      dom.inputMessageSubject.value = template.subject;
      dom.inputMessageTimeout.value = template.timeout;
      acePayload.setValue(JSON.stringify(template.payload, null, 2), -1);
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
  const payload = acePayload.getValue();
  aceResponse.setValue('');
  API.callDittoREST('POST', '/things/' + Things.theThing.thingId +
      '/features/' + dom.theFeatureId.value +
      '/inbox/messages/' + dom.inputMessageSubject.value +
      '?timeout=' + dom.inputMessageTimeout.value,
  payload,
  ).then((data) => {
    if (dom.inputMessageTimeout.value > 0) {
      aceResponse.setValue(JSON.stringify(data, null, 2), -1);
    }
  }).catch((err) => {
    aceResponse.setValue('');
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
  acePayload.setValue('{}');
  aceResponse.setValue('');
  dom.ulMessageTemplates.innerHTML = '';
}

function refillTemplates() {
  dom.ulMessageTemplates.innerHTML = '';
  Utils.addDropDownEntries(dom.ulMessageTemplates, ['Saved message templates'], true);
  if (dom.theFeatureId.value && Environments.current().messageTemplates[dom.theFeatureId.value]) {
    Utils.addDropDownEntries(
        dom.ulMessageTemplates,
        Object.keys(Environments.current().messageTemplates[dom.theFeatureId.value]),
    );
  }
}

function onFeatureChanged(featureId) {
  clearAllFields();
  refillTemplates();
}

