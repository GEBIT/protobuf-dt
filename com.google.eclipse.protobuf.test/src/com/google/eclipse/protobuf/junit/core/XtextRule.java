/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.junit.core;

import static java.util.Arrays.asList;
import static org.eclipse.xtext.util.Strings.isEmpty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ISetup;
import org.eclipse.xtext.XtextPackage;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.IResourceFactory;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.ui.resource.IResourceUIServiceProvider;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ProtobufPackage;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * JUnit <code>{@link MethodRule}</code> that:
 * <ol>
 * <li>Configures standalone Xtext services, with fixture-owned validator and resource services</li>
 * <li>Creates an <code>{@link XtextResource}</code> from method-level comments</li>
 * <li>Creates .proto files in the file system based on method-level comments (if the comment starts with
 * "// Create file" followed by the name of the file to create)</li>
 * <li>Finds model objects and nodes in the created <code>{@link XtextResource}</code> (from #2)</li>
 * <li>Restores global registrations after setup and after each method, including failures</li>
 * </ol>
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class XtextRule implements MethodRule {
  private static final Object REGISTRY_LOCK = new Object();

  private final Injector injector;
  private final Registrations registrations;

  private final CommentReader commentReader;
  private final FileCreator fileCreator;
  private final ProtobufInTestsParser protobufParser;

  private Protobuf root;
  private XtextResource resource;
  private Finder finder;

  public static XtextRule overrideRuntimeModuleWith(Module...testModules) {
    return createWith(new OverrideRuntimeModuleSetup(testModules));
  }

  public static XtextRule createWith(ISetup setup) {
    synchronized (REGISTRY_LOCK) {
      Registrations previous = new Registrations();
      try {
        // The registrar mutates an existing composite, so saving its reference alone is insufficient.
        EValidator.Registry.INSTANCE.remove(ProtobufPackage.eINSTANCE);
        return createWith(setup.createInjectorAndDoEMFRegistration());
      } finally {
        previous.restore();
      }
    }
  }

  public static XtextRule createWith(Injector injector) {
    return new XtextRule(injector);
  }

  private XtextRule(Injector injector) {
    this.injector = injector;
    registrations = new Registrations();
    commentReader = new CommentReader();
    fileCreator = new FileCreator();
    protobufParser = new ProtobufInTestsParser(injector);
  }

  @Override public Statement apply(final Statement base, final FrameworkMethod method, final Object target) {
    return new Statement() {
      @Override public void evaluate() throws Throwable {
        synchronized (REGISTRY_LOCK) {
          Registrations previous = new Registrations();
          try {
            registrations.restore();
            registerFixtureServices();
            root = null;
            resource = null;
            finder = null;
            injector.injectMembers(target);
            String comments = commentsIn(method);
            if (!isEmpty(comments)) {
              parseText(comments);
            }
            base.evaluate();
          } finally {
            previous.restore();
          }
        }
      }
    };
  }

  private void registerFixtureServices() {
    EValidator validator = injector.getInstance(EValidator.Registry.class).getEValidator(ProtobufPackage.eINSTANCE);
    if (validator != null) {
      EValidator.Registry.INSTANCE.put(ProtobufPackage.eINSTANCE, validator);
    }
    IResourceFactory factory = injector.getInstance(IResourceFactory.class);
    Binding<org.eclipse.xtext.ui.editor.IURIEditorOpener> uiOpener = injector.getExistingBinding(
        Key.get(org.eclipse.xtext.ui.editor.IURIEditorOpener.class, org.eclipse.xtext.ui.LanguageSpecific.class));
    IResourceServiceProvider provider = uiOpener == null ? injector.getInstance(IResourceServiceProvider.class)
        : injector.getInstance(IResourceUIServiceProvider.class);
    for (String extension : new String[] { "proto", "protodevel" }) {
      Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put(extension, factory);
      IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().put(extension, provider);
    }
  }

  private String commentsIn(FrameworkMethod method) {
    String source = null;
    for (String comment : commentReader.commentsIn(method)) {
      File protoFile = fileCreator.createFileFrom(comment);
      if (protoFile == null && source == null) {
        source = comment;
      }
    }
    return source;
  }

  public void parseText(String text) {
    root = null;
    resource = null;
    finder = null;
    IParseResult parseResult = protobufParser.parseText(text);
    root = (Protobuf) parseResult.getRootASTElement();
    if (root != null) {
      resource = (XtextResource) root.eResource();
      finder = new Finder(parseResult.getRootNode(), parseResult.getRootNode().getText());
    }
  }

  public Injector injector() {
    return injector;
  }

  public XtextResource resource() {
    return resource;
  }

  public Protobuf root() {
    return root;
  }

  public String text() {
    return resource.getParseResult().getRootNode().getText();
  }

  public <T extends EObject> T find(String name, String extra, Class<T> type, SearchOption...options) {
    return find(name + extra, name.length(), type, options);
  }

  public <T extends EObject> T find(String name, Class<T> type, SearchOption...options) {
    return find(name, name.length(), type, options);
  }

  public <T extends EObject> T find(String text, int count, Class<T> type, SearchOption...options) {
    return finder.find(text, count, type, asList(options));
  }

  public ILeafNode findNode(String text) {
    return finder.find(text);
  }

  public <T extends EObject> List<T> findAll(Class<T> type) {
    return EcoreUtil2.getAllContentsOfType(root, type);
  }

  public <T extends EObject> T findFirst(Class<T> type) {
    return findAll(type).get(0);
  }

  private static final class Registrations {
    private final List<RegistryEntry<?, ?>> entries = new ArrayList<RegistryEntry<?, ?>>();

    Registrations() {
      entries.add(new RegistryEntry<EPackage, Object>(EValidator.Registry.INSTANCE, ProtobufPackage.eINSTANCE));
      for (String extension : new String[] { "proto", "protodevel", "ecore", "xmi", "xtextbin" }) {
        entries.add(new RegistryEntry<String, Object>(
            Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap(), extension));
      }
      for (String extension : new String[] { "proto", "protodevel" }) {
        entries.add(new RegistryEntry<String, Object>(
            IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap(), extension));
      }
      entries.add(new RegistryEntry<String, Object>(EPackage.Registry.INSTANCE, ProtobufPackage.eNS_URI));
      entries.add(new RegistryEntry<String, Object>(EPackage.Registry.INSTANCE, XtextPackage.eNS_URI));
    }

    void restore() {
      for (RegistryEntry<?, ?> entry : entries) {
        entry.restore();
      }
    }
  }

  private static final class RegistryEntry<K, V> {
    private final Map<K, V> registry;
    private final K key;
    private final boolean present;
    private final V value;

    RegistryEntry(Map<K, V> registry, K key) {
      this.registry = registry;
      this.key = key;
      boolean found = false;
      V previous = null;
      // Registry.get may resolve a descriptor and mutate the entry we are trying to preserve.
      for (Map.Entry<K, V> entry : registry.entrySet()) {
        if (key.equals(entry.getKey())) {
          found = true;
          previous = entry.getValue();
          break;
        }
      }
      present = found;
      value = previous;
    }

    void restore() {
      if (present) {
        registry.put(key, value);
      } else {
        registry.remove(key);
      }
    }
  }
}
