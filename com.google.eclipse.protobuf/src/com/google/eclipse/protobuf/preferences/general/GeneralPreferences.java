/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.preferences.general;

import static com.google.eclipse.protobuf.preferences.general.PreferenceNames.ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME;
import static com.google.eclipse.protobuf.preferences.general.PreferenceNames.VALIDATE_FILES_ON_ACTIVATION;

import com.google.eclipse.protobuf.preferences.DefaultPreservingInitializer;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;

/**
 * Reads the currently effective workspace/project settings. Listeners observe both writable stores,
 * including project-setting toggles; their owner must remove them when its lifecycle ends.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class GeneralPreferences {
  private final IPreferenceStore workspaceStore;
  private final IPreferenceStore projectStore;

  public GeneralPreferences(IPreferenceStoreAccess storeAccess, IProject project) {
    workspaceStore = storeAccess.getWritablePreferenceStore();
    projectStore = project == null ? workspaceStore : storeAccess.getWritablePreferenceStore(project);
  }

  private IPreferenceStore effectiveStore() {
    return projectStore.getBoolean(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME) ? projectStore : workspaceStore;
  }

  public boolean shouldValidateFilesOnActivation() {
    return effectiveStore().getBoolean(VALIDATE_FILES_ON_ACTIVATION);
  }

  public String getDescriptorProtoPath() {
    return effectiveStore().getString(PreferenceNames.DESCRIPTOR_PROTO_PATH);
  }

  public void addPropertyChangeListener(IPropertyChangeListener listener) {
    workspaceStore.addPropertyChangeListener(listener);
    if (projectStore != workspaceStore) {
      projectStore.addPropertyChangeListener(listener);
    }
  }

  public void removePropertyChangeListener(IPropertyChangeListener listener) {
    workspaceStore.removePropertyChangeListener(listener);
    if (projectStore != workspaceStore) {
      projectStore.removePropertyChangeListener(listener);
    }
  }

  public static class Initializer extends DefaultPreservingInitializer {
    @Override
    public void setDefaults() {
      setDefault(ENABLE_PROJECT_SETTINGS_PREFERENCE_NAME, false);
      setDefault(VALIDATE_FILES_ON_ACTIVATION, true);
      setDefault(PreferenceNames.DESCRIPTOR_PROTO_PATH, PreferenceNames.DEFAULT_DESCRIPTOR_PATH);
    }
  }
}
