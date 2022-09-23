/* eslint-disable comma-dangle */
/* eslint-disable prefer-const */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Environments from '../environments/environments.js';
import * as Things from '../things/things.js';

let thePolicy;
let selectedEntry;
let selectedSubject;
let selectedResource;

let policyTemplates;

let dom = {
  inputPolicyId: null,
  inputPolicyEntry: null,
  inputSubjectId: null,
  inputResourceId: null,
  tbodyPolicyEntries: null,
  tbodyPolicySubjects: null,
  tbodyPolicyResources: null,
  tbodyWhoami: null,
  buttonLoadPolicy: null,
  buttonCreatePolicyEntry: null,
  buttonDeletePolicyEntry: null,
  buttonCreatePolicySubject: null,
  buttonDeletePolicySubject: null,
  buttonSavePolicySubject: null,
  buttonCreatePolicyResource: null,
  buttonDeletePolicyResource: null,
  buttonSavePolicyResource: null,
  tableValidationEntries: null,
  tableValidationSubjects: null,
  tableValidationResources: null,
  collapsePolicies: null,
  ulResourceTemplates: null,
  tabPolicies: null,
};

let subjectEditor;
let resourceEditor;

export function ready() {
  Environments.addChangeListener(onEnvironmentChanged);

  Utils.getAllElementsById(dom);

  loadPolicyTemplates();

  Utils.addValidatorToTable(dom.tbodyPolicyEntries, dom.tableValidationEntries);
  Utils.addValidatorToTable(dom.tbodyPolicySubjects, dom.tableValidationSubjects);
  Utils.addValidatorToTable(dom.tbodyPolicySubjects, dom.tableValidationResources);

  subjectEditor = Utils.createAceEditor('subjectEditor', 'ace/mode/json');
  resourceEditor = Utils.createAceEditor('resourceEditor', 'ace/mode/json');

  dom.tabPolicies.onclick = onTabActivated;

  dom.buttonLoadPolicy.onclick = () => {
    Utils.assert(dom.inputPolicyId.value, 'Please enter a policyId', dom.inputPolicyId);
    refreshPolicy(dom.inputPolicyId.value);
  };

  // Entries

  dom.tbodyPolicyEntries.onclick = (event) => {
    if (selectedEntry === event.target.textContent) {
      selectedEntry = null;
      selectedResource = null;
      selectedSubject = null;
      Utils.tableAdjustSelection(tbodyWhoami, () => false);
      setEntry(null);
    } else {
      selectedEntry = event.target.textContent;
      setEntry(thePolicy, selectedEntry);
    }
  };

  dom.buttonCreatePolicyEntry.onclick = () => {
    validations(true);
    Utils.assert(!Object.keys(thePolicy.entries).includes(dom.inputPolicyEntry.value),
        `Entry with label ${dom.inputPolicyEntry.value} already exists`, dom.inputPolicyEntry);
    selectedEntry = dom.inputPolicyEntry.value;
    putOrDeletePolicyEntry(dom.inputPolicyEntry.value, {subjects: {}, resources: {}});
  };

  dom.buttonDeletePolicyEntry.onclick = () => {
    validations(false, true);
    putOrDeletePolicyEntry(selectedEntry);
  };

  // Subjects --------

  dom.tbodyPolicySubjects.onclick = (event) => {
    if (selectedSubject === event.target.parentNode.id) {
      selectedSubject = null;
      Utils.tableAdjustSelection(tbodyWhoami, () => false);
      subjectEditor.setValue('');
    } else {
      selectedSubject = event.target.parentNode.id;
      Utils.tableAdjustSelection(tbodyWhoami, (row) => row.id === selectedSubject);
      subjectEditor.setValue(
          JSON.stringify(thePolicy.entries[selectedEntry].subjects[selectedSubject], null, 2), -1);
    }
    dom.inputSubjectId.value = selectedSubject;
  };

  dom.buttonCreatePolicySubject.onclick = () => {
    validations(false, true, true);
    Utils.assert(!Object.keys(thePolicy.entries[selectedEntry].subjects).includes(dom.inputSubjectId.value),
        `Subject already exists`, dom.inputSubjectId);
    selectedSubject = dom.inputSubjectId.value;
    modifyPolicyEntry('/subjects/', dom.inputSubjectId.value,
        subjectEditor.getValue() !== '' ?
        JSON.parse(subjectEditor.getValue()) :
        {type: 'generated'});
  };

  dom.buttonSavePolicySubject.onclick = () => {
    validations(false, true, false, true);
    if (dom.inputSubjectId.value === selectedSubject) {
      modifyPolicyEntry('/subjects/', selectedSubject, JSON.parse(subjectEditor.getValue()));
    } else {
      modifyPolicyEntry('/subjects/', selectedSubject);
      selectedSubject = dom.inputSubjectId.value;
      modifyPolicyEntry('/subjects/', dom.inputSubjectId.value,
          subjectEditor.getValue() !== '' ?
          JSON.parse(subjectEditor.getValue()) :
          {type: 'generated'});
    }
  };

  dom.buttonDeletePolicySubject.onclick = () => {
    validations(false, true, false, true);
    modifyPolicyEntry('/subjects/', selectedSubject);
  };

  // Resources -----------

  dom.tbodyPolicyResources.onclick = (event) => {
    if (selectedResource === event.target.parentNode.id) {
      selectedResource = null;
      resourceEditor.setValue('');
    } else {
      selectedResource = event.target.parentNode.id;
      resourceEditor.setValue(
          JSON.stringify(thePolicy.entries[selectedEntry].resources[selectedResource], null, 2), -1);
    }
    dom.inputResourceId.value = selectedResource;
  };

  dom. buttonCreatePolicyResource.onclick = () => {
    validations(false, true, false, false, true);
    Utils.assert(!Object.keys(thePolicy.entries[selectedEntry].resources).includes(dom.inputResourceId.value),
        `Resource already exists`, dom.inputResourceId);
    selectedResource = dom.inputResourceId.value;
    modifyPolicyEntry('/resources/', dom.inputResourceId.value,
        resourceEditor.getValue() !== '' ?
        JSON.parse(resourceEditor.getValue()) :
        {grant: ['READ', 'WRITE'], revoke: []});
  };

  dom.buttonSavePolicyResource.onclick = () => {
    validations(false, true, false, false, false, true);
    if (dom.inputResourceId.value === selectedResource) {
      modifyPolicyEntry('/resources/', selectedResource, JSON.parse(resourceEditor.getValue()));
    } else {
      modifyPolicyEntry('/resources/', selectedResource);
      selectedResource = dom.inputResourceId.value;
      modifyPolicyEntry('/resources/', dom.inputResourceId.value,
          resourceEditor.getValue() !== '' ?
          JSON.parse(resourceEditor.getValue()) :
          {grant: ['READ', 'WRITE'], revoke: []});
    }
  };

  dom.buttonDeletePolicyResource.onclick = () => {
    validations(false, true, false, false, false, true);
    modifyPolicyEntry('/resources/', selectedResource);
  };

  dom.ulResourceTemplates.addEventListener('click', (event) => {
    dom.inputResourceId.value = event.target.textContent;
    resourceEditor.setValue(JSON.stringify(policyTemplates.resources[dom.inputResourceId.value], 0, 2), -1);
  });

  // WhoAmI -------------

  dom.tbodyWhoami.onclick = (event) => {
    dom.inputSubjectId.value = event.target.parentNode.id;
  };

  Things.addChangeListener(onThingChanged);
}

function validations(entryFilled, entrySelected, subjectFilled, subjectSelected, resourceFilled, resourceSelected) {
  Utils.assert(thePolicy, 'Please load a policy', dom.inputPolicyId);
  if (entryFilled) {
    Utils.assert(dom.inputPolicyEntry.value, 'Please enter a label for the entry', dom.inputPolicyEntry);
  }
  if (entrySelected) {
    Utils.assert(selectedEntry, 'Please select an entry', dom.tableValidationEntries);
  }
  if (subjectFilled) {
    Utils.assert(dom.inputSubjectId.value, 'Please enter a subject or select one above', dom.inputSubjectId);
  };
  if (subjectSelected) {
    Utils.assert(selectedSubject, 'Please select a subject', dom.tableValidationSubjects);
  };
  if (resourceFilled) {
    Utils.assert(dom.inputResourceId.value, 'Please enter a resource', dom.inputResourceId);
  };
  if (resourceSelected) {
    Utils.assert(selectedResource, 'Please select a resource', dom.tableValidationResources);
  };
}

function onThingChanged(thing) {
  dom.inputPolicyId.value = (thing && thing.policyId) ? thing.policyId : null;
  viewDirty = true;
}

function refreshWhoAmI() {
  dom.tbodyWhoami.innerHTML = '';
  API.callDittoREST('GET', '/whoami')
      .then((whoamiResult) => {
        whoamiResult.subjects.forEach((subject) => {
          Utils.addTableRow(dom.tbodyWhoami, subject, subject === selectedSubject, false,
            subject === whoamiResult.defaultSubject ? 'default' : '');
        });
      })
      .catch((error) => {
      });
};

function refreshPolicy(policyId) {
  API.callDittoREST('GET', '/policies/' + policyId)
      .then((policy) => setThePolicy(policy))
      .catch(() => setThePolicy());
};

function setThePolicy(policy) {
  thePolicy = policy;

  dom.tbodyPolicyEntries.innerHTML = '';
  dom.inputPolicyEntry.value = null;

  if (thePolicy) {
    let policyHasEntry = false;
    Object.keys(thePolicy.entries).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicyEntries, key, key === selectedEntry);
      if (key === selectedEntry) {
        dom.inputPolicyEntry.value = key;
        setEntry(thePolicy, key);
        policyHasEntry = true;
      }
    });
    if (!policyHasEntry) {
      selectedEntry = null;
      setEntry(null);
    }
  } else {
    dom.inputPolicyId.value = null;
    setEntry(null);
  }
};

function setEntry(policy, entryLabel) {
  dom.tbodyPolicySubjects.innerHTML = '';
  dom.tbodyPolicyResources.innerHTML = '';
  dom.inputSubjectId.value = null;
  dom.inputResourceId.value = null;
  subjectEditor.setValue('');
  resourceEditor.setValue('');
  if (policy) {
    dom.inputPolicyEntry.value = entryLabel;
    Object.keys(policy.entries[entryLabel].subjects).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicySubjects, key, key === selectedSubject, false,
          JSON.stringify(policy.entries[entryLabel].subjects[key])
      );
      if (key === selectedSubject) {
        dom.inputSubjectId.value = key;
        subjectEditor.setValue(JSON.stringify(policy.entries[entryLabel].subjects[key], null, 2), -1);
      }
    });
    Object.keys(policy.entries[entryLabel].resources).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicyResources, key, key === selectedResource, false,
          JSON.stringify(policy.entries[entryLabel].resources[key])
      );
      if (key === selectedResource) {
        dom.inputResourceId.value = key;
        resourceEditor.setValue(JSON.stringify(policy.entries[entryLabel].resources[key], null, 2), -1);
      }
    });
  }
};

function putOrDeletePolicyEntry(entry, value) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${thePolicy.policyId}/entries/${entry}`,
      value
  ).then(() => refreshPolicy(thePolicy.policyId));
};

function modifyPolicyEntry(type, key, value) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${thePolicy.policyId}/entries/${selectedEntry}${type}${key}`, value
  ).then(() => refreshPolicy(thePolicy.policyId));
};

let viewDirty = false;

function onTabActivated() {
  if (viewDirty) {
    refreshWhoAmI();
    if (dom.inputPolicyId.value) {
      refreshPolicy(dom.inputPolicyId.value);
    }
    viewDirty = false;
  }
}

function onEnvironmentChanged(modifiedField) {
  if (!['pinnedThings', 'filterList', 'authorization'].includes(modifiedField)) {
    setThePolicy(null);
  }
  if (dom.collapsePolicies.classList.contains('show')) {
    refreshWhoAmI();
  } else {
    viewDirty = true;
  }
}

function loadPolicyTemplates() {
  fetch('templates/policyTemplates.json')
      .then((response) => {
        response.json().then((loadedTemplates) => {
          policyTemplates = loadedTemplates;
          Utils.addDropDownEntries(dom.ulResourceTemplates, Object.keys(policyTemplates.resources));
        });
      });
}

