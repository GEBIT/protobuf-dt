/*
 * Copyright (c) 2026 GEBIT Solutions GmbH.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.parser;

import static com.google.eclipse.protobuf.validation.Messages.misplacedHeader;
import static com.google.eclipse.protobuf.validation.Messages.expectedOptionValue;
import static org.eclipse.xtext.diagnostics.Diagnostic.SYNTAX_DIAGNOSTIC;

import org.eclipse.emf.common.util.TreeIterator;
import org.antlr.runtime.CharStream;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.nodemodel.impl.NodeModelBuilder;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.ParseResult;

import com.google.eclipse.protobuf.parser.antlr.ProtobufParser;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.validation.Messages;
import com.google.eclipse.protobuf.protobuf.MisplacedHeader;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.Protobuf;

/** Preserves incomplete source constructs while reporting real node-model syntax errors. */
public class RecoveringProtobufParser extends ProtobufParser {
  @Override protected IParseResult doParse(String ruleName, CharStream in, NodeModelBuilder builder,
      int initialLookAhead) {
    IParseResult result = super.doParse(ruleName, in, builder, initialLookAhead);
    EObject root = result.getRootASTElement();
    boolean recovered = false;
    if (root instanceof Protobuf) {
      Protobuf protobuf = (Protobuf) root;
      recovered = !protobuf.getMisplacedHeaders().isEmpty();
      if (recovered) {
        SyntaxErrorMessage error = new SyntaxErrorMessage(misplacedHeader, SYNTAX_DIAGNOSTIC);
        for (MisplacedHeader header : protobuf.getMisplacedHeaders()) {
          markHeader(header, builder, error);
        }
      }
    } else if (root instanceof MisplacedHeader) {
      // Partial reparses of a recovery header must not turn it into an accepted header.
      markHeader((MisplacedHeader) root, builder, new SyntaxErrorMessage(misplacedHeader, SYNTAX_DIAGNOSTIC));
      recovered = true;
    }
    if (root != null) {
      recovered |= markMissingValue(root, builder);
      recovered |= markIncompleteDeclaration(root, builder);
      for (TreeIterator<EObject> contents = root.eAllContents(); contents.hasNext();) {
        EObject object = contents.next();
        recovered |= markMissingValue(object, builder);
        recovered |= markIncompleteDeclaration(object, builder);
      }
    }
    return recovered ? new ParseResult(root, result.getRootNode(), true) : result;
  }

  private boolean markMissingValue(EObject object, NodeModelBuilder builder) {
    if (!(object instanceof AbstractOption) || ((AbstractOption) object).getValue() != null) return false;
    ICompositeNode node = NodeModelUtils.getNode(object);
    if (node == null) return false;
    for (INode nested : node.getAsTreeIterable()) {
      if (nested.getSyntaxErrorMessage() != null) return false;
    }
    for (ILeafNode leaf : node.getLeafNodes()) {
      if (!leaf.isHidden() && "=".equals(leaf.getText())) {
        builder.setSyntaxError(leaf, new SyntaxErrorMessage(expectedOptionValue, SYNTAX_DIAGNOSTIC));
        return true;
      }
    }
    return false;
  }

  private boolean markIncompleteDeclaration(EObject object, NodeModelBuilder builder) {
    if (!(object instanceof MessageField || object instanceof Group)) return false;
    ICompositeNode node = NodeModelUtils.getNode(object);
    if (node == null) return false;
    for (INode nested : node.getAsTreeIterable()) {
      if (nested.getSyntaxErrorMessage() != null) return false;
    }
    long index = object instanceof MessageField ? ((MessageField) object).getIndex() : ((Group) object).getIndex();
    if (index == 0 && NodeModelUtils.findNodesForFeature(object, object.eClass().getEStructuralFeature("index")).isEmpty()) {
      for (INode name : NodeModelUtils.findNodesForFeature(object, object.eClass().getEStructuralFeature("name"))) {
        for (ILeafNode leaf : name.getLeafNodes()) {
          if (!leaf.isHidden()) {
            builder.setSyntaxError(leaf, new SyntaxErrorMessage(Messages.expectedFieldNumber, SYNTAX_DIAGNOSTIC));
            return true;
          }
        }
      }
    }
    if (object instanceof MessageField && node.getEndOffset() > node.getOffset()) {
      ILeafNode last = NodeModelUtils.findLeafNodeAtOffset(node, node.getEndOffset() - 1);
      if (last != null && !";".equals(last.getText())) {
        builder.setSyntaxError(last, new SyntaxErrorMessage(Messages.expectedFieldTerminator, SYNTAX_DIAGNOSTIC));
        return true;
      }
    }
    return false;
  }

  private void markHeader(MisplacedHeader header, NodeModelBuilder builder, SyntaxErrorMessage error) {
    // Mark the actual keyword, not the composite: a partial parse can make the latter the root node.
    for (ILeafNode node : NodeModelUtils.getNode(header).getLeafNodes()) {
      if (!node.isHidden()) {
        if (node.getSyntaxErrorMessage() == null) builder.setSyntaxError(node, error);
        return;
      }
    }
  }
}
