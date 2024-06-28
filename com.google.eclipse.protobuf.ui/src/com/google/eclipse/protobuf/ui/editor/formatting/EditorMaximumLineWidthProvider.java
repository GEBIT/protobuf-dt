package com.google.eclipse.protobuf.ui.editor.formatting;

import com.google.eclipse.protobuf.formatting.IMaximumLineWidthProvider;
import com.google.eclipse.protobuf.ui.preferences.editor.general.EditorPreferences;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class EditorMaximumLineWidthProvider implements IMaximumLineWidthProvider {

	@Inject private Provider<EditorPreferences> preferencesProvider;
	
	@Override
	public int maximumLineWidth() {
		return preferencesProvider.get().maximumLineWidth();
	}

}
