package com.google.eclipse.protobuf.ui.editor.formatting;

import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.xtext.formatting.IIndentationInformation;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;

import com.google.eclipse.protobuf.ui.preferences.editor.general.EditorPreferences;
import com.google.eclipse.protobuf.ui.preferences.editor.save.SaveActionsPreferences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class CustomPreferenceStoreIndentationInformation implements IIndentationInformation {

	@Inject private Provider<EditorPreferences> preferencesProvider;

	public int getTabWidth() {
		return 2; // storeAccess.getPreferenceStore().getInt(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH);
	}

	public boolean isSpacesForTab() {
		return preferencesProvider.get().shouldInsertSpacesForTabs();
	}

	// note: the maximum length allowed in the eclipse preferences dialog is 16
	private final String WS = "                                     ";

	private String indentString = null;

	@Override
	public synchronized String getIndentString() {
		return isSpacesForTab() ? "  " : "\t";
	}

}