/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.scoping;

import static com.google.eclipse.protobuf.util.Workspaces.workspaceRoot;
import static java.util.Collections.unmodifiableList;

import com.google.common.collect.ImmutableList;
import com.google.eclipse.protobuf.scoping.IUriResolver;
import com.google.eclipse.protobuf.ui.preferences.paths.DirectoryPath;
import com.google.eclipse.protobuf.ui.preferences.paths.PathsPreferences;
import com.google.inject.Inject;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.xtext.ui.XtextProjectHelper;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Resolves URIs.
 */
public class UriResolver implements IUriResolver {
  @Inject private MultipleDirectoriesUriResolver multipleDirectories;
  @Inject private SingleDirectoryUriResolver singleDirectory;
  @Inject private IPreferenceStoreAccess storeAccess;

  @Override
  public String resolveUri(String importUri, URI declaringResourceUri, IProject project) {
    return resolveUriInternal(importUri, declaringResourceUri, project, false);
  }

  private String resolveUriInternal(String importUri, URI declaringResourceUri, IProject project, boolean recursive) {
    if (project == null) {
      return multipleDirectories.resolveUri(importUri, preferencesFromAllProjects());
    }
    PathsPreferences locations = new PathsPreferences(storeAccess, project);
    String resolvedUri = null;
    if (locations.areFilesInMultipleDirectories()) {
      resolvedUri = multipleDirectories.resolveUri(importUri, ImmutableList.of(locations));
    } else {
      if (recursive) {
    	  // In case of recursive dependency resolution without explicitly configured directories from a Java
    	  // project, try to add all source directories to the search path list, because that's the best we can do
    	  if (project.isOpen() && JavaProject.hasJavaNature(project)) {
    		  ArrayList<DirectoryPath> paths = new ArrayList<>();
    		  try {
	    		  IJavaProject javaProject = JavaCore.create(project);
	    		  for (IPackageFragmentRoot packageFragmentRoot : javaProject.getAllPackageFragmentRoots()) {
	    			  if (packageFragmentRoot.getKind() == IPackageFragmentRoot.K_SOURCE) {
	    				  paths.add(DirectoryPath.parse("${workspace_loc:" + packageFragmentRoot.getPath().toOSString() + "}", project));
	    			  }
	    		  }
    		  } catch(JavaModelException e) {
    			  // ignored
    		  }
    		  
    		  resolvedUri = multipleDirectories.resolveUriFromPaths(importUri, paths);
    	  }
      } else {
    	  resolvedUri = singleDirectory.resolveUri(importUri, declaringResourceUri);
      }
    }
    if (resolvedUri != null) {
      return resolvedUri;
    }
	
	try {
		// Try to resolve in the dependencies. First search referenced projects.
		for (IProject refProject : project.getReferencedProjects()) {
			resolvedUri = resolveUriInternal(importUri, declaringResourceUri, refProject, true);
			if (resolvedUri != null) {
		      return resolvedUri;
			}
		}
		
		// Then search referenced jar dependencies, in case it's a Java project
		if (project.isOpen() && JavaProject.hasJavaNature(project)) {
  		  ArrayList<DirectoryPath> paths = new ArrayList<>();
  		  try {
				final Consumer<IClasspathEntry> cpeConsumer = new Consumer<IClasspathEntry>() {
					@Override
					public void accept(IClasspathEntry classpathEntry) {
						if (classpathEntry.getPath().toOSString().toLowerCase().endsWith(".jar")) {
					  IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
				
					  if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
					      IPath cpPath = classpathEntry.getPath();
					      IResource res = root.findMember(cpPath);
					      // If res is null, the path is absolute (it's an external jar)
					      String path;
					      if (res == null) {
					          path = cpPath.toOSString();
					      } else {
					          path = "${workspace_loc:" + res.getFullPath().toOSString() + "}";
						      }
						      paths.add(DirectoryPath.parse(path, project));
						  }
					  }  					
					}  				
				};
  			  
  			  
				IJavaProject javaProject = JavaCore.create(project);    		  
				for (IClasspathEntry classpathEntry : javaProject.getRawClasspath()) {
					if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
						final IClasspathContainer container = JavaCore.getClasspathContainer(classpathEntry.getPath(), javaProject);
						if(container.getKind() != IClasspathContainer.K_SYSTEM && container.getKind() != IClasspathContainer.K_DEFAULT_SYSTEM) {
							for (IClasspathEntry containedClasspathEntry : container.getClasspathEntries()) {
								cpeConsumer.accept(containedClasspathEntry);
							}
						}
					} else if (classpathEntry.getContentKind() == IPackageFragmentRoot.K_BINARY) {
						cpeConsumer.accept(classpathEntry);
					}
				}
  		  } catch(JavaModelException e) {
  			  // ignored
  		  }
  		  
  		  resolvedUri = multipleDirectories.resolveUriFromPaths(importUri, paths);
  		  if (resolvedUri != null) {
		      return resolvedUri;
  		  }
  	  }
	} catch (CoreException e) {
		throw new RuntimeException(e);
	}
	return null;
  }

  private Iterable<PathsPreferences> preferencesFromAllProjects() {
    List<PathsPreferences> allPreferences = new ArrayList<>();
    IWorkspaceRoot root = workspaceRoot();
    for (IProject project : root.getProjects()) {
      if (project.isHidden() || !project.isAccessible() || !XtextProjectHelper.hasNature(project)) {
        continue;
      }
      PathsPreferences preferences = new PathsPreferences(storeAccess, project);
      allPreferences.add(preferences);
    }
    return unmodifiableList(allPreferences);
  }
}
