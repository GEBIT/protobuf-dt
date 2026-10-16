/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor.model;

import static org.eclipse.core.filebuffers.FileBuffers.getTextFileBufferManager;
import static org.eclipse.core.filebuffers.LocationKind.IFILE;
import static org.eclipse.core.filebuffers.LocationKind.NORMALIZE;
import static org.eclipse.text.undo.DocumentUndoManagerRegistry.getDocumentUndoManager;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.eclipse.protobuf.ui.util.IStatusFactory.error;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.xtext.builder.impl.BuildScheduler;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.ui.editor.model.XtextDocument;
import org.eclipse.xtext.ui.editor.model.XtextDocumentProvider;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;

import com.google.eclipse.protobuf.preferences.general.GeneralPreferences;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.ui.editor.UriEditorInput;
import com.google.eclipse.protobuf.ui.preferences.editor.save.SaveActionsPreferences;
import com.google.eclipse.protobuf.ui.preferences.paths.PathsPreferences;
import com.google.eclipse.protobuf.ui.util.editor.ChangedLineRegionCalculator;
import com.google.eclipse.protobuf.util.EResources;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Keeps preference subscriptions for the connected documents only. Refreshing the semantic input
 * never writes document text, so unsaved edits, undo history and the provider's dirty state survive.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufDocumentProvider extends XtextDocumentProvider {
  @SuppressWarnings("restriction")
  @Inject private BuildScheduler buildScheduler;
  @Inject private IPreferenceStoreAccess storeAccess;
  @Inject private ProtoDescriptorProvider descriptors;
  @Inject private IResourceScopeCache scopeCache;

  private final Map<XtextDocument, LiveDocument> liveDocuments = new IdentityHashMap<>();
  private final Map<IProject, ProjectPreferences> liveProjects = new HashMap<>();
  private final Set<LiveDocument> pendingRefresh = new HashSet<>();
  private final Set<IProject> pendingBuild = new HashSet<>();
  private boolean refreshQueued;
  private final IResourceChangeListener resourceListener = this::resourcesChanged;
  private Runnable detachDependencyListener;
  private static final IRegion[] NO_CHANGE = new IRegion[0];

  @Inject private ChangedLineRegionCalculator calculator;
  @Inject private Provider<SaveActionsPreferences> preferencesProvider;
  @Inject private SaveActions saveActions;
  @Inject private Provider<IDocumentPartitioner> partitionerProvider;

  private final List<DocumentContentsFactory> documentFactories;

  @Inject public ProtobufDocumentProvider(FileStoreDocumentContentsFactory f1, UriDocumentContentsFactory f2) {
    documentFactories = newArrayList(f1, f2);
  }

  @Override protected IDocument createDocument(Object element) throws CoreException {
    DocumentContentsFactory factory = findDocumentFactory(element);
    if (factory != null) {
      return createDocument(factory, element);
    }
    return super.createDocument(element);
  }

  private DocumentContentsFactory findDocumentFactory(Object element) {
    if (element instanceof IEditorInput) {
      IEditorInput input = (IEditorInput) element;
      for (DocumentContentsFactory factory : documentFactories) {
        if (factory.supportsEditorInputType(input)) {
          return factory;
        }
      }
    }
    return null;
  }

  private IDocument createDocument(DocumentContentsFactory contentsFactory, Object element) throws CoreException {
    XtextDocument document = createEmptyDocument();
    contentsFactory.createContents(document, element);
    IDocumentPartitioner partitioner = partitionerProvider.get();
    partitioner.connect(document);
    document.setDocumentPartitioner(partitioner);
    return document;
  }

  @Override protected IAnnotationModel createAnnotationModel(Object element) throws CoreException {
    return element instanceof UriEditorInput ? new AnnotationModel() : super.createAnnotationModel(element);
  }

  @Override public boolean isReadOnly(Object element) {
    return isArchiveInput(element) || super.isReadOnly(element);
  }

  @Override public boolean isModifiable(Object element) {
    return !isArchiveInput(element) && super.isModifiable(element);
  }

  @Override public boolean canSaveDocument(Object element) {
    return !isArchiveInput(element) && super.canSaveDocument(element);
  }

  private boolean isArchiveInput(Object element) {
    return element instanceof UriEditorInput && ((UriEditorInput) element).getFileUri().isArchive();
  }

  @Override protected ElementInfo createElementInfo(Object element) throws CoreException {
    ElementInfo info = super.createElementInfo(element);
    if (info != null && info.fDocument instanceof XtextDocument) {
      XtextDocument document = (XtextDocument) info.fDocument;
      Resource resource = document.readOnly(state -> state);
      if (resource != null) {
        synchronized (liveDocuments) {
          IProject project = EResources.getProjectOf(resource);
          ProjectPreferences preferences = liveProjects.get(project);
          if (preferences == null) {
            preferences = new ProjectPreferences(project);
            liveProjects.put(project, preferences);
          }
          preferences.connections++;
          liveDocuments.put(document, new LiveDocument(document, resource, preferences, Display.getDefault()));
          requestRefresh(preferences, false);
        }
      }
    }
    return info;
  }

  @Override protected void disposeElementInfo(Object element, ElementInfo info) {
    synchronized (liveDocuments) {
      LiveDocument live = liveDocuments.remove(info.fDocument);
      if (live != null) {
        pendingRefresh.remove(live);
        ProjectPreferences preferences = live.preferences;
        if (--preferences.connections == 0) {
          preferences.preferences.removePropertyChangeListener(preferences);
          liveProjects.remove(preferences.project);
          pendingBuild.remove(preferences.project);
        }
      }
    }
    super.disposeElementInfo(element, info);
  }

  @Override protected void connected() {
    super.connected();
    ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener, IResourceChangeEvent.POST_CHANGE);
    if (Platform.getBundle("org.eclipse.jdt.core") != null) {
      JavaDependencyChanges listener = new JavaDependencyChanges();
      JavaCore.addElementChangedListener(listener, ElementChangedEvent.POST_CHANGE);
      detachDependencyListener = () -> JavaCore.removeElementChangedListener(listener);
    }
  }

  @Override protected void disconnected() {
    ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
    if (detachDependencyListener != null) {
      detachDependencyListener.run();
      detachDependencyListener = null;
    }
    synchronized (liveDocuments) {
      pendingRefresh.clear();
      pendingBuild.clear();
    }
    super.disconnected();
  }

  private final class JavaDependencyChanges implements IElementChangedListener {
    @Override public void elementChanged(ElementChangedEvent event) {
      Set<IProject> changed = new HashSet<>();
      collectChangedProjects(event.getDelta(), changed);
      refreshDependentProjects(changed, true);
    }

    private void collectChangedProjects(IJavaElementDelta delta, Set<IProject> changed) {
      int dependencyFlags = IJavaElementDelta.F_CLASSPATH_CHANGED | IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
          | IJavaElementDelta.F_ADDED_TO_CLASSPATH | IJavaElementDelta.F_REMOVED_FROM_CLASSPATH
          | IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED | IJavaElementDelta.F_REORDER;
      if ((delta.getFlags() & dependencyFlags) != 0) {
        IJavaProject javaProject = delta.getElement().getJavaProject();
        if (javaProject != null) {
          changed.add(javaProject.getProject());
        }
      }
      if (delta.getElement().getElementType() >= IJavaElement.PACKAGE_FRAGMENT_ROOT) {
        return;
      }
      for (IJavaElementDelta child : delta.getAffectedChildren()) {
        collectChangedProjects(child, changed);
      }
    }
  }

  private final class ProjectPreferences implements IPropertyChangeListener {
    final IProject project;
    final GeneralPreferences preferences;
    int connections;

    ProjectPreferences(IProject project) {
      this.project = project;
      preferences = new GeneralPreferences(storeAccess, project);
      preferences.addPropertyChangeListener(this);
    }

    @Override public void propertyChange(PropertyChangeEvent event) {
      String property = event.getProperty();
      if (property.startsWith("general.") || property.startsWith("paths.")) {
        requestRefresh(this, true);
      }
    }
  }

  private static final class LiveDocument {
    final XtextDocument document;
    final Resource resource;
    final ProjectPreferences preferences;
    final Display display;

    LiveDocument(XtextDocument document, Resource resource, ProjectPreferences preferences, Display display) {
      this.document = document;
      this.resource = resource;
      this.preferences = preferences;
      this.display = display;
    }
  }

  private void requestRefresh(ProjectPreferences preferences, boolean build) {
    synchronized (liveDocuments) {
      Display display = null;
      for (LiveDocument live : liveDocuments.values()) {
        if (live.preferences == preferences) {
          pendingRefresh.add(live);
          display = live.display;
        }
      }
      if (display == null || display.isDisposed()) {
        return;
      }
      if (build && preferences.project != null) {
        pendingBuild.add(preferences.project);
      }
      if (!refreshQueued) {
        refreshQueued = true;
        display.asyncExec(this::refreshDocuments);
      }
    }
  }

  @SuppressWarnings("restriction")
  private void refreshDocuments() {
    List<LiveDocument> documents;
    Set<IProject> projects;
    Set<Resource> connectedResources;
    synchronized (liveDocuments) {
      documents = newArrayList(pendingRefresh);
      projects = new HashSet<>(pendingBuild);
      connectedResources = new HashSet<>();
      for (LiveDocument live : liveDocuments.values()) {
        connectedResources.add(live.resource);
      }
      pendingRefresh.clear();
      pendingBuild.clear();
      refreshQueued = false;
    }
    Set<IProject> invalidated = new HashSet<>();
    for (LiveDocument live : documents) {
      synchronized (liveDocuments) {
        if (liveDocuments.get(live.document) != live) {
          continue;
        }
      }
      if (invalidated.add(live.preferences.project)) {
        descriptors.invalidateCacheFor(live.preferences.project);
      }
      // internalModify notifies normal dirty-state/model listeners and schedules annotation validation.
      // Reparse from the document, not storage: no document event or save-state change is introduced.
      live.document.internalModify(new IUnitOfWork.Void<XtextResource>() {
        @Override public void process(XtextResource resource) throws Exception {
          if (resource == null || resource.getResourceSet() == null) {
            return;
          }
          ResourceSet set = resource.getResourceSet();
          for (Resource imported : newArrayList(set.getResources())) {
            scopeCache.clear(imported);
            if (!connectedResources.contains(imported)) {
              imported.unload();
              set.getResources().remove(imported);
            }
          }
          resource.reparse(live.document.get());
        }
      });
    }
    // The Xtext scheduler refreshes persisted project diagnostics through its ordinary builder.
    buildScheduler.scheduleBuildIfNecessary(projects);
  }

  private void resourcesChanged(IResourceChangeEvent event) {
    IResourceDelta delta = event.getDelta();
    if (delta == null) {
      return;
    }
    Set<IProject> changedProjects = new HashSet<>();
    Set<IProject> configurationChanges = new HashSet<>();
    List<IResource> changedResources = newArrayList();
    try {
      delta.accept(change -> {
        IResource resource = change.getResource();
        if (resource.getType() == IResource.ROOT) {
          return true;
        }
        String name = resource.getName();
        boolean configuration = ".project".equals(name) || ".classpath".equals(name) || "pom.xml".equals(name)
            || name.endsWith(".prefs") || name.endsWith(".jar") || name.endsWith(".zip");
        if (resource.getType() == IResource.FILE && !configuration && !"proto".equals(resource.getFileExtension())) {
          return false;
        }
        int flags = change.getFlags();
        if (change.getKind() != IResourceDelta.CHANGED
            || (flags & (IResourceDelta.CONTENT | IResourceDelta.REPLACED | IResourceDelta.DESCRIPTION
                | IResourceDelta.OPEN | IResourceDelta.LOCAL_CHANGED)) != 0) {
          changedProjects.add(resource.getProject());
          changedResources.add(resource);
          if (configuration || "descriptor.proto".equals(name) || resource.getType() == IResource.PROJECT) {
            configurationChanges.add(resource.getProject());
          }
        }
        return true;
      });
    } catch (CoreException e) {
      // A concurrently closed project will be handled by the next workspace delta.
      return;
    }
    synchronized (liveDocuments) {
      if (liveDocuments.isEmpty() || changedResources.isEmpty()) {
        return;
      }
      Display display = liveDocuments.values().iterator().next().display;
      if (!display.isDisposed()) {
        // Workspace callbacks must not acquire a document lock while holding a workspace lock.
        display.asyncExec(() -> refreshWorkspaceDocuments(changedResources, changedProjects, configurationChanges));
      }
    }
  }

  private void refreshWorkspaceDocuments(List<IResource> changes, Set<IProject> changedProjects,
      Set<IProject> configurationChanges) {
    List<LiveDocument> documents;
    synchronized (liveDocuments) {
      documents = newArrayList(liveDocuments.values());
    }
    for (LiveDocument live : documents) {
      boolean[] metadataChanged = { false };
      boolean[] externalChange = { false };
      boolean affected = live.document.readOnly(resource -> {
        if (resource == null || resource.getResourceSet() == null) {
          return false;
        }
        URI descriptor = descriptors.primaryDescriptor(resource).location;
        metadataChanged[0] = refersToChange(descriptor, changes);
        if (metadataChanged[0]
            || dependsOnChangedProject(live.preferences.project, changedProjects, new HashSet<IProject>())) {
          return true;
        }
        for (Resource imported : resource.getResourceSet().getResources()) {
          if (refersToChange(imported.getURI(), changes)) {
            externalChange[0] = true;
            return true;
          }
        }
        PathsPreferences paths = new PathsPreferences(storeAccess, live.preferences.project);
        if (!paths.areFilesInMultipleDirectories()) {
          return false;
        }
        return Boolean.TRUE.equals(paths.applyToEachDirectoryPath(path -> {
          IPath root = Path.fromOSString(path.value());
          for (IResource changed : changes) {
            IPath location = path.isWorkspacePath() ? changed.getFullPath() : changed.getLocation();
            if (location != null && root.isPrefixOf(location)) {
              externalChange[0] = true;
              return Boolean.TRUE;
            }
          }
          return null;
        }));
      });
      if (affected) {
        boolean build = metadataChanged[0] || externalChange[0]
            || dependsOnChangedProject(live.preferences.project, configurationChanges, new HashSet<IProject>());
        requestRefresh(live.preferences, build);
      }
    }
  }

  private boolean refersToChange(URI uri, List<IResource> changes) {
    if (uri == null) {
      return false;
    }
    String value = uri.toString();
    for (IResource changed : changes) {
      URI workspaceUri = URI.createPlatformResourceURI(changed.getFullPath().toPortableString(), true);
      if (refersTo(value, workspaceUri, changed.getType() != IResource.FILE)) {
        return true;
      }
      IPath location = changed.getLocation();
      if (location != null && refersTo(value, URI.createFileURI(location.toOSString()), changed.getType() != IResource.FILE)) {
        return true;
      }
    }
    return false;
  }

  private boolean refersTo(String value, URI changed, boolean directory) {
    String prefix = changed.toString();
    return value.equals(prefix) || value.startsWith("archive:" + prefix + "!/")
        || (directory && (value.startsWith(prefix + "/") || value.startsWith("archive:" + prefix + "/")));
  }

  private void refreshDependentProjects(Set<IProject> changed, boolean build) {
    if (changed.isEmpty()) {
      return;
    }
    List<ProjectPreferences> preferences;
    synchronized (liveDocuments) {
      preferences = newArrayList(liveProjects.values());
    }
    for (ProjectPreferences project : preferences) {
      if (dependsOnChangedProject(project.project, changed, new HashSet<IProject>())) {
        requestRefresh(project, build);
      }
    }
  }

  private boolean dependsOnChangedProject(IProject project, Set<IProject> changed, Set<IProject> visited) {
    if (project == null || !visited.add(project)) {
      return false;
    }
    if (changed.contains(project)) {
      return true;
    }
    try {
      if (project.isAccessible()) {
        for (IProject dependency : project.getReferencedProjects()) {
          if (dependsOnChangedProject(dependency, changed, visited)) {
            return true;
          }
        }
      }
    } catch (CoreException e) {
      return true;
    }
    return false;
  }

  @Override protected void doSaveDocument(IProgressMonitor monitor, Object element, IDocument document,
      boolean overwrite) throws CoreException {
    if (isArchiveInput(element)) {
      throw new CoreException(error("Archive members are read-only."));
    }
    if (element instanceof IFileEditorInput) {
      performSaveActions(monitor, (IFileEditorInput) element, document);
    }
    super.doSaveDocument(monitor, element, document, overwrite);
  }

  private void performSaveActions(IProgressMonitor monitor,
      IFileEditorInput editorInput, IDocument document) throws CoreException {
    IRegion[] changedRegions = changedRegions(monitor, editorInput, document);
    TextEdit edit = saveActions.createSaveAction(document, changedRegions);
    if (edit == null) {
      return;
    }
    try {
      IDocumentUndoManager manager = getDocumentUndoManager(document);
      manager.beginCompoundChange();
      edit.apply(document);
      manager.endCompoundChange();
    } catch (Throwable t) {
      throw new CoreException(error(t));
    }
  }

  private IRegion[] changedRegions(IProgressMonitor monitor, IFileEditorInput editorInput, IDocument document)
      throws CoreException {
    SaveActionsPreferences preferences = preferencesProvider.get();
    if (!preferences.shouldRemoveTrailingWhitespace()) {
      return NO_CHANGE;
    }
    if (preferences.shouldRemoveTrailingWhitespaceInEditedLines()) {
      return calculator.calculateChangedLineRegions(textFileBuffer(monitor, editorInput), document, monitor);
    }
    return new IRegion[] { new Region(0, document.getLength()) };
  }

  private ITextFileBuffer textFileBuffer(IProgressMonitor monitor, IFileEditorInput editorInput) throws CoreException {
    IPath location = editorInput.getFile().getFullPath();
    ITextFileBufferManager textFileBufferManager = getTextFileBufferManager();
    textFileBufferManager.connect(location, NORMALIZE, monitor);
    try {
      return textFileBufferManager.getTextFileBuffer(location, IFILE);
    } finally {
      textFileBufferManager.disconnect(location, NORMALIZE, monitor);
    }
  }
}
