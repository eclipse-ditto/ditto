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

/* eslint-disable comma-dangle */
/* eslint-disable prefer-const */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Things from '../things/things.js';
import * as Environments from '../environments/environments.js';
import {TabHandler} from '../utils/tabHandler.js';
import policyHTML from './policies.html';
import { Observable } from '../utils/observable.js';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';

export interface Policy {
  policyId: string,
  entries: Object,
  imports: Object,
}

export let observable = Observable();

export let thePolicy: Policy;

let tabHandler;

type DomElements = {
  inputPolicyId: HTMLInputElement,
  tbodyRecentPolicies: HTMLTableElement,
  tbodyWhoami: HTMLTableElement,
  buttonLoadPolicy: HTMLButtonElement,
  collapsePolicies: HTMLElement,
  tabPolicies: HTMLUListElement,
}

let dom: DomElements = {
  inputPolicyId: null,
  tbodyRecentPolicies: null,
  tbodyWhoami: null,
  buttonLoadPolicy: null,
  collapsePolicies: null,
  tabPolicies: null,
} ;

document.getElementById('policyHTML').innerHTML = policyHTML;

export function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);
  tabHandler = TabHandler(dom.tabPolicies, dom.collapsePolicies, refreshAll, 'disablePolicies');

  dom.tbodyRecentPolicies.addEventListener('click', onRecentPoliciesTableClicked); 

  dom.buttonLoadPolicy.onclick = () => {
    Utils.assert(dom.inputPolicyId.value, 'Please enter a policyId', dom.inputPolicyId);
    refreshPolicy(dom.inputPolicyId.value);
  };

  

  // WhoAmI -------------

  dom.tbodyWhoami.onclick = (event) => {
    // dom.crudSubject.idValue = event.target.parentNode.id;
  };

  Things.addChangeListener(onThingChanged);
}



// function validations(entryFilled, entrySelected = false, subjectFilled = false, subjectSelected = false, resourceFilled = false, resourceSelected = false) {
//   Utils.assert(thePolicy, 'Please load a policy', dom.inputPolicyId);
//   if (entryFilled) {
//     Utils.assert(dom.crudEntry.idValue, 'Please enter a label for the entry', dom.crudEntry.validationElement);
//   }
//   if (entrySelected) {
//     Utils.assert(selectedEntry, 'Please select an entry', dom.tableValidationEntries);
//   }
//   if (subjectFilled) {
//     Utils.assert(dom.crudSubject.idValue, 'Please enter a subject or select one above', dom.crudSubject.validationElement);
//   };
//   if (subjectSelected) {
//     Utils.assert(selectedSubject, 'Please select a subject', dom.tableValidationSubjects);
//   };
//   if (resourceFilled) {
//     Utils.assert(dom.crudResource.idValue, 'Please enter a resource', dom.crudResource.validationElement);
//   };
//   if (resourceSelected) {
//     Utils.assert(selectedResource, 'Please select a resource', dom.tableValidationResources);
//   };
// }

function onThingChanged(thing) {
  dom.inputPolicyId.value = thing && thing.policyId;
  tabHandler.viewDirty = true;
}

function refreshWhoAmI() {
  dom.tbodyWhoami.innerHTML = '';
  API.callDittoREST('GET', '/whoami')
      .then((whoamiResult) => {
        whoamiResult.subjects.forEach((subject) => {
          Utils.addTableRow(dom.tbodyWhoami, subject, false, subject,
            subject === whoamiResult.defaultSubject ? 'default' : '');
        });
      })
      .catch((error) => {
      });
};

export function refreshPolicy(policyId) {
  API.callDittoREST('GET', '/policies/' + policyId)
      .then((policy) => setThePolicy(policy))
      .catch(() => setThePolicy(null));
};

export function setThePolicy(policy: Policy) {
  thePolicy = policy;
  
  if (thePolicy) {
    updateRecentPolicies(thePolicy.policyId);
  } else {
    dom.inputPolicyId.value = null;
  }
  observable.notifyAll(thePolicy);
};


function refreshAll(otherEnvironment: boolean) {
  refreshWhoAmI();
  if (otherEnvironment) {
    setThePolicy(null);
  }
  if (dom.inputPolicyId.value) {
    refreshPolicy(dom.inputPolicyId.value);
  }
}

export function updateRecentPolicies(policyId: String) {
  const oldIndex = Environments.current().recentPolicyIds.indexOf(policyId);
  if (oldIndex >= 0) {
    Environments.current().recentPolicyIds.splice(oldIndex,1);
  }
  Environments.current().recentPolicyIds.unshift(policyId);
  if (Environments.current().recentPolicyIds.length >= 10) {
    Environments.current().recentPolicyIds.pop();
  }

  Environments.environmentsJsonChanged('recentPolicyIds');
}

function onEnvironmentChanged(modifiedField: String) {
  Environments.current()['recentPolicyIds'] = Environments.current()['recentPolicyIds'] || [];
  dom.tbodyRecentPolicies.innerHTML = '';
  Environments.current().recentPolicyIds.forEach(entry => {
    Utils.addTableRow(dom.tbodyRecentPolicies, entry, thePolicy && thePolicy.policyId === entry, entry);
  });
}

function onRecentPoliciesTableClicked(event) {
  if (event.target && event.target.nodeName === 'TD') {
    const row = event.target.parentNode;
    if (thePolicy && thePolicy.policyId === row.id) {
      setThePolicy(null);
    } else {
      dom.inputPolicyId.value = row.id;
      refreshPolicy(row.id);
    }
  }
}

export function finishEditing(editor: CrudToolbar, op: CrudOperation) {
  return () => {
    if (op in [CrudOperation.CREATE, CrudOperation.UPDATE]) {
      editor.toggleEdit(false);
    }
    refreshPolicy(thePolicy.policyId);
  }
}


