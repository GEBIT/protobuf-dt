package com.google.eclipse.protobuf.ui.editor.hyperlinking;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.text.Region;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.hyperlinking.AbstractHyperlink;
import org.eclipse.xtext.ui.editor.hyperlinking.HyperlinkHelper;
import org.eclipse.xtext.ui.editor.hyperlinking.IHyperlinkAcceptor;

import com.google.eclipse.protobuf.ui.editor.ProtobufUriEditorOpener;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;
import com.google.inject.Provider;

/** Model-driven symbol links retain the invoking document's project even after editor activation changes. */
public class ProtobufHyperlinkHelper extends HyperlinkHelper {
  @Inject private Provider<SymbolHyperlink> links;

  @Override public void createHyperlinksTo(XtextResource from, Region region, EObject target,
      IHyperlinkAcceptor acceptor) {
    URI uri = EcoreUtil.getURI(target);
    SymbolHyperlink link = links.get();
    link.uri = uri.isPlatformResource() ? uri : from.getResourceSet().getURIConverter().normalize(uri);
    link.project = EResources.getProjectOf(from);
    link.setHyperlinkRegion(region);
    link.setHyperlinkText(getLabelProvider().getText(target));
    acceptor.accept(link);
  }

  private static final class SymbolHyperlink extends AbstractHyperlink {
    private final ProtobufUriEditorOpener opener;
    private URI uri;
    private IProject project;

    @Inject SymbolHyperlink(ProtobufUriEditorOpener opener) {
      this.opener = opener;
    }

    @Override public void open() {
      opener.open(uri, null, -1, true, project);
    }
  }
}
