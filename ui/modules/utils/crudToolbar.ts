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
import * as Utils from '../utils.js';
import crudToolbarHTML from './crudToolbar.html';

class CrudToolbar extends HTMLElement {
  isEditing = false;
  isEditDisabled = false;
  isCreateDisabled = false;
  isDeleteDisabled = false;
  dom = {
    label: null,
    inputIdValue: null,
    buttonEdit: null,
    buttonCreate: null,
    buttonUpdate: null,
    buttonDelete: null,
    buttonCancel: null,
    divRoot: null,
  };

  static get observedAttributes() {
    return [`extraeditclass`];
  }

  private _extraEditClass: string;

  get extraeditclass() {
    return this._extraEditClass;
  }

  set extraeditclass(val: string) {
    if (val == null) { // check for null and undefined
      this.removeAttribute('extraeditclass');
    }
    else {
      this.setAttribute('extraeditclass', val);
    }
  }

  get idValue() {
    return this.dom.inputIdValue.value;
  }

  attributeChangedCallback(name, oldValue, newValue) {
    if (name === `extraeditclass`) {
      this._extraEditClass = newValue;
    }
  }

  set idValue(newValue) {
    (this.shadowRoot.getElementById('inputIdValue') as HTMLInputElement).value = newValue;
    const buttonDelete = this.shadowRoot.getElementById('buttonDelete');
    if (!this.isDeleteDisabled && newValue && newValue !== '') {
      buttonDelete.removeAttribute('hidden');
    } else {
      buttonDelete.setAttribute('hidden', '');
    }
  }

  get editDisabled() {
    return this.isEditDisabled;
  }

  set createDisabled(newValue) {
    this.isCreateDisabled = newValue;
    this.setButtonState('buttonCreate', newValue);
  }

  set deleteDisabled(newValue) {
    this.isDeleteDisabled = newValue;
    this.setButtonState('buttonDelete', newValue);
  }

  set editDisabled(newValue) {
    this.isEditDisabled = newValue;
    if (!this.isEditing) {
      this.setButtonState('buttonEdit', newValue);
    }
  }

  setButtonState(buttonId, isDisabled) {
    const button = this.shadowRoot.getElementById(buttonId);

    if (isDisabled) {
      button.setAttribute('hidden', '');
    } else {
      button.removeAttribute('hidden');
    }
  }
  get validationElement() {
    return this.dom.inputIdValue;
  }

  constructor() {
    super();
    this.attachShadow({mode: 'open'});
  }

  connectedCallback() {
    this.shadowRoot.innerHTML = crudToolbarHTML;

    setTimeout(() => {
      Utils.getAllElementsById(this.dom, this.shadowRoot);

      this.dom.buttonEdit.onclick = () => this.toggleEdit(false);
      this.dom.buttonCancel.onclick = () => this.toggleEdit(true);
      this.dom.label.innerText = this.getAttribute('label') || 'Label';
      this.dom.buttonCreate.onclick = this.eventDispatcher('onCreateClick');
      this.dom.buttonUpdate.onclick = this.eventDispatcher('onUpdateClick');
      this.dom.buttonDelete.onclick = this.eventDispatcher('onDeleteClick');
    });
  };

  eventDispatcher(eventName) {
    return () => {
      this.dispatchEvent(new CustomEvent(eventName, {
        composed: true,
      }));
    };
  }

  toggleEdit(isCancel) {
    this.isEditing = !this.isEditing;
    document.getElementById('modalCrudEdit').classList.toggle('editBackground');
    this.dom.divRoot.classList.toggle('editForground');
    if (this._extraEditClass) {
      this.dom.divRoot.classList.toggle(this._extraEditClass);
    }

    if (this.isEditing || this.isEditDisabled) {
      this.dom.buttonEdit.setAttribute('hidden', '');
    } else {
      this.dom.buttonEdit.removeAttribute('hidden');
    }
    this.dom.buttonCancel.toggleAttribute('hidden');

    if (this.isEditing) {
      if (this.dom.inputIdValue.value) {
        this.dom.buttonUpdate.toggleAttribute('hidden');
      } else if (!this.isCreateDisabled) {
        this.dom.buttonCreate.toggleAttribute('hidden');
      }
    } else {
      this.dom.buttonCreate.setAttribute('hidden', '');
      this.dom.buttonUpdate.setAttribute('hidden', '');
    }
    if (this.isEditing || !this.dom.inputIdValue.value) {
      this.dom.buttonDelete.setAttribute('hidden', '');
    }
    const allowIdChange = this.isEditing && (!this.dom.inputIdValue.value || this.hasAttribute('allowIdChange'));
    this.dom.inputIdValue.disabled = !allowIdChange;
    this.dispatchEvent(new CustomEvent('onEditToggle', {
      composed: true,
      detail: {
        isEditing: this.isEditing,
        isCancel: isCancel,
      },
    }));
  }
}

customElements.define('crud-toolbar', CrudToolbar);
