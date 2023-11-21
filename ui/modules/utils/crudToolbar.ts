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

const CLASS_BG = 'editBackground'
const CLASS_FG = 'editForground';
const CLASS_FG_FULLSCREEN = 'editForgroundBig';

let ICON_CLASS_FS = 'bi-arrows-fullscreen';
let ICON_CLASS_FS_EXIT = 'bi-fullscreen-exit';

const ATTR_FULLSCREEN = 'fullscreen';

export enum CrudOperation {
  CREATE,
  UPDATE,
  DELETE,
}

export class CrudToolbar extends HTMLElement {
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
    buttonFullscreen: null,
    divRoot: null,
  };

  get idValue() {
    return this.dom.inputIdValue.value;
  }

  set idValue(newValue) {
    const domInput: HTMLInputElement = this.shadowRoot.getElementById('inputIdValue') as HTMLInputElement; 
    domInput.value = newValue;
    domInput.classList.remove('is-invalid');

    const buttonEdit = this.shadowRoot.getElementById('buttonEdit');
    if (newValue && newValue !== '') {
      buttonEdit.innerText = 'Edit';
    } else {
      buttonEdit.innerText = 'Create'
    }

    const buttonDelete = this.shadowRoot.getElementById('buttonDelete');
    if (!this.isEditing && !this.isDeleteDisabled && newValue && newValue !== '') {
      buttonDelete.removeAttribute('hidden');
    } else {
      buttonDelete.setAttribute('hidden', '');
    }
  }

  get editDisabled() {
    return this.isEditDisabled;
  }

  set createDisabled(newValue: boolean) {
    this.isCreateDisabled = newValue;
    this.lazyInit(this.dom.buttonCreate);
    this.setButtonState(this.dom.buttonCreate, newValue);
  }

  set deleteDisabled(newValue: boolean) {
    this.isDeleteDisabled = newValue;
    this.lazyInit(this.dom.buttonDelete);
    this.setButtonState(this.dom.buttonDelete, newValue);
  }

  set editDisabled(newValue: boolean) {
    this.isEditDisabled = newValue;
    if (!this.isEditing) {
      this.lazyInit(this.dom.buttonEdit);
      this.setButtonState(this.dom.buttonEdit, newValue);
    }
  }

  lazyInit(element: HTMLElement) {
    if (!element) {
      Utils.getAllElementsById(this.dom, this.shadowRoot);
    }
  }

  setButtonState(button: HTMLButtonElement, isDisabled: boolean) {
    // const button = this.shadowRoot.getElementById(buttonId);

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
      this.dom.buttonFullscreen.onclick = () => this.toggleFullscreen();
      this.dom.inputIdValue.addEventListener('change', (event) => {
        (event.target as HTMLElement).classList.remove('is-invalid');
        this.dispatchEvent(new CustomEvent('onIdValueChange', { composed: true }));
      });
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
  
    // toggle modal mode;
    document.getElementById('modalCrudEdit').classList.toggle(CLASS_BG);
    document.body.classList.toggle('modal-open');
    document.body.style.overflow = this.isEditing && this.hasAttribute(ATTR_FULLSCREEN) ? 'hidden' : '';
    this.dom.divRoot.classList.toggle(this.hasAttribute(ATTR_FULLSCREEN) ? CLASS_FG_FULLSCREEN : CLASS_FG);

    // toggle button states;
    this.setButtonState(this.dom.buttonEdit, this.isEditing || this.isEditDisabled);
    this.setButtonState(this.dom.buttonFullscreen, !this.isEditing);
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

  toggleFullscreen() {
    this.toggleAttribute(ATTR_FULLSCREEN);
    this.dom.buttonFullscreen.querySelector('.bi').classList.toggle(ICON_CLASS_FS);
    this.dom.buttonFullscreen.querySelector('.bi').classList.toggle(ICON_CLASS_FS_EXIT);
    document.body.style.overflow = this.isEditing && this.hasAttribute(ATTR_FULLSCREEN) ? 'hidden' : '';
    if (this.dom.divRoot.classList.contains(CLASS_FG)) {
      this.dom.divRoot.classList.replace(CLASS_FG, CLASS_FG_FULLSCREEN);
    } else {
      this.dom.divRoot.classList.replace(CLASS_FG_FULLSCREEN, CLASS_FG);
    }
  }
}

customElements.define('crud-toolbar', CrudToolbar);
