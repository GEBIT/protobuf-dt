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

import static com.google.eclipse.protobuf.junit.core.SearchOption.IGNORE_CASE;
import static com.google.eclipse.protobuf.util.SystemProperties.lineSeparator;

import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.nodemodel.BidiTreeIterator;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.impl.AbstractNode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.eclipse.protobuf.protobuf.DefaultValueFieldOption;
import com.google.eclipse.protobuf.protobuf.Package;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
class Finder {
  private static final String[] FEATURE_NAMES = { "name", "source", "target", "type", "value", "importedNamespace" };

  private final String protoAsText;
  private final AbstractNode root;

  Finder(INode node, String protoAsText) {
    this.root = rootOf(node);
    this.protoAsText = protoAsText;
  }

  private static AbstractNode rootOf(INode node) {
    while (!(node instanceof AbstractNode)) {
      node = node.getParent();
    }
    return (AbstractNode) node;
  }

  <T extends EObject> T find(String text, int count, Class<T> type, List<SearchOption> options) {
    int offset = protoAsText.indexOf(text);
    String name = text.substring(0, count);
    Iterator<AbstractNode> iterator = root.basicIterator();
    while (iterator.hasNext()) {
      AbstractNode node = iterator.next();
      int nodeOffset = node.getOffset();
      if (nodeOffset > offset || (nodeOffset + node.getLength()) <= offset) {
        continue;
      }
      EObject e = node.getSemanticElement();
      if (isDefaultValueFieldOption(name, type, e)) {
        return type.cast(e);
      }
      if (type.isInstance(e)) {
        if (areNamesEqual(name, e, options)) {
          return type.cast(e);
        }
      }
    }
    String format = "Unable to find element. Text: '%s', count: %d, type: %s, options: %s";
    throw new AssertionError(String.format(format, text, count, type.getName(), options));
  }

  private boolean isDefaultValueFieldOption(String name, Class<?> type, EObject element) {
    return "default".equals(name) && type.isInstance(element) && element instanceof DefaultValueFieldOption;
  }

  private boolean areNamesEqual(String expected, Object e, List<SearchOption> options) {
    String actual = nameOf(e);
    if (!(e instanceof Package)) {
      expected = simpleName(expected);
      actual = simpleName(actual);
    }
    if (options.contains(IGNORE_CASE)) {
      return expected.equalsIgnoreCase(actual);
    }
    return expected.equals(actual);
  }

  private String simpleName(String name) {
    if (name == null) {
      return null;
    }
    String result = name.substring(name.lastIndexOf('.') + 1);
    return result.startsWith("^") ? result.substring(1) : result;
  }

  private String nameOf(Object o) {
    if (!(o instanceof EObject)) {
      return null;
    }
    EObject e = (EObject) o;
    for (String name : FEATURE_NAMES) {
      EStructuralFeature feature = e.eClass().getEStructuralFeature(name);
      if (feature == null || feature.isMany()) {
        continue;
      }
      if (feature instanceof EReference && !((EReference) feature).isContainment()) {
        // Locate the written reference, even when its declaration is intentionally unresolved.
        List<INode> nodes = NodeModelUtils.findNodesForFeature(e, feature);
        if (!nodes.isEmpty()) {
          StringBuilder text = new StringBuilder();
          for (ILeafNode leaf : nodes.get(0).getLeafNodes()) {
            if (!leaf.isHidden()) {
              text.append(leaf.getText());
            }
          }
          if (text.length() != 0) {
            return text.toString();
          }
        }
      }
      Object value = e.eGet(feature, false);
      if (value instanceof String) {
        return (String) value;
      }
      String nestedName = nameOf(value);
      if (nestedName != null) {
        return nestedName;
      }
    }
    return null;
  }

  ILeafNode find(String text) {
    BidiTreeIterator<AbstractNode> iterator = root.basicIterator();
    while (iterator.hasNext()) {
      AbstractNode node = iterator.next();
      if (!(node instanceof ILeafNode)) {
        continue;
      }
      String nodeText = clean(node.getText());
      if (text.equals(nodeText)) {
        return (ILeafNode) node;
      }
    }
    String format = "Unable to find node. Text: '%s'";
    throw new AssertionError(String.format(format, text));
  }

  private String clean(String text) {
    return text.replace(lineSeparator(), " ").trim();
  }
}
