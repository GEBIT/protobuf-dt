/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.contentassist;

import static com.google.eclipse.protobuf.grammar.CommonKeyword.CLOSING_BRACKET;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.EDITION;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.SYNTAX;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.LITERAL;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.DEFAULT_EQUAL_IN_BRACKETS;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.DEFAULT_EQUAL_STRING_IN_BRACKETS;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EMPTY_STRING;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EQUAL_PROTO2_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EQUAL_PROTO3_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EDITION_2023_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EDITION_2024_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EQUAL_EDITION_2023_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.EQUAL_EDITION_2024_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.PROTO2_IN_QUOTES;
import static com.google.eclipse.protobuf.ui.grammar.CompoundElement.PROTO3_IN_QUOTES;
import static com.google.eclipse.protobuf.util.CommonWords.space;
import static java.lang.String.valueOf;
import static org.eclipse.xtext.util.Strings.toFirstLower;

import com.google.eclipse.protobuf.grammar.CommonKeyword;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.eclipse.protobuf.model.util.FeatureSets;
import com.google.eclipse.protobuf.validation.EditionValidator;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Predicate;
import com.google.eclipse.protobuf.model.util.OptionValues;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.Extensions;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.JsonNameFieldOption;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MapTypeLink;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.OneOf;
import com.google.eclipse.protobuf.protobuf.ScalarType;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.TypeExtension;
import com.google.eclipse.protobuf.protobuf.TypeLink;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.eclipse.protobuf.protobuf.Option;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.Rpc;
import com.google.eclipse.protobuf.protobuf.Service;
import com.google.eclipse.protobuf.protobuf.Stream;
import com.google.eclipse.protobuf.scoping.ProtoDescriptorProvider;
import com.google.eclipse.protobuf.services.ProtobufGrammarAccess;
import com.google.eclipse.protobuf.model.util.IndexedElements;
import com.google.eclipse.protobuf.model.util.Literals;
import com.google.eclipse.protobuf.model.util.MessageFields;
import com.google.eclipse.protobuf.model.util.Options;
import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.ComplexValue;
import com.google.eclipse.protobuf.protobuf.DefaultValueFieldOption;
import com.google.eclipse.protobuf.protobuf.Enum;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.ui.grammar.CompoundElement;
import com.google.eclipse.protobuf.ui.labeling.Images;
import com.google.inject.Inject;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.AbstractRule;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ui.PluginImageHelper;
import org.eclipse.xtext.ui.editor.contentassist.ConfigurableCompletionProposal;
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor;


/**
 * @author alruiz@google.com (Alex Ruiz)
 *
 * @see <a href="http://www.eclipse.org/Xtext/documentation/310_eclipse_support.html#content-assist">Xtext Content Assist</a>
 */
public class ProtobufProposalProvider extends AbstractProtobufProposalProvider {
  @Inject private Images images;
  @Inject private IndexedElements indexedElements;
  @Inject private PluginImageHelper imageHelper;
  @Inject private Literals literals;
  @Inject private MessageFields messageFields;
  @Inject private OptionValues optionValues;
  @Inject private Syntaxes syntaxes;
  @Inject private IQualifiedNameProvider qualifiedNames;
  @Inject private Options options;
  @Inject private ProtoDescriptorProvider descriptorProvider;
  @Inject private ProtobufGrammarAccess grammar;
  @Inject private FeatureSets features;
  @Inject private EditionValidator editionValidator;
  private ContentAssistContext proposalContext;
  private String proposalMapPosition;
  private String proposalStatement;
  private String proposalLastToken;
  private boolean proposalRangeOptions;
  private boolean proposalRangeRoot;
  private int proposalRangeAggregateDepth;
  private boolean proposalRangeValue;
  private String proposalRangeOption;

  @Override public void createProposals(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    String prefix = context.getPrefix();
    if (prefix.length() == 1 && "([=,:.<{".indexOf(prefix.charAt(0)) >= 0) {
      context = context.copy().setPrefix("")
          .setReplaceRegion(new org.eclipse.jface.text.Region(context.getOffset(), 0)).toContext();
    }
    proposalContext = context;
    proposalMapPosition = calculateMapPosition(context);
    try {
      if ("key".equals(proposalMapPosition)) {
        for (ScalarType type : ScalarType.VALUES) {
          if (type != ScalarType.DOUBLE && type != ScalarType.FLOAT && type != ScalarType.BYTES) {
            proposeAndAccept(type.toString(), context, acceptor);
          }
        }
        return;
      }
      if (proposalRangeOptions && proposalRangeRoot && (proposalRangeValue || "[".equals(proposalLastToken)
          || ",".equals(proposalLastToken) || !context.getPrefix().isEmpty())) {
        if (proposalRangeValue) {
          proposeFieldValue(rangeOptionType(context), context, acceptor);
        } else {
          proposeNativeOptions(context, acceptor);
        }
        return;
      }
      if (proposalRangeOptions && proposalRangeAggregateDepth == 1
          && ("{".equals(proposalLastToken) || ",".equals(proposalLastToken)
              || ";".equals(proposalLastToken) || !context.getPrefix().isEmpty())) {
        proposeMembers(rangeOptionType(context), false, context, acceptor);
        return;
      }
      if ("option".equals(proposalStatement) && "(".equals(proposalLastToken)) {
        AbstractOption option = enclosing(context, AbstractOption.class);
        AbstractRule rule = options.rootSourceOf(option) == null ? grammar.getOptionSourceRule()
            : grammar.getExtensionOptionFieldRule();
        for (CrossReference reference : EcoreUtil2.getAllContentsOfType(rule, CrossReference.class)) {
          lookupCrossReference(reference, context, acceptor);
        }
        return;
      }
      super.createProposals(context, acceptor);
    } finally {
      proposalContext = null;
      proposalMapPosition = null;
      proposalStatement = null;
      proposalLastToken = null;
      proposalRangeOptions = false;
      proposalRangeRoot = false;
      proposalRangeAggregateDepth = 0;
      proposalRangeValue = false;
      proposalRangeOption = null;
    }
  }

  private EObject rangeOptionType(ContentAssistContext context) {
    for (MessageField field : messageFields.fieldsOf(nativeOptionsMessage(context))) {
      if (field.getName().equals(proposalRangeOption)) {
        return optionValues.typeOf(field);
      }
    }
    return null;
  }

  @Override public void completeProtobuf_Syntax(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {}

  @Override public void completeProtobuf_Edition(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {}

  @Override public void completeProtobuf_MisplacedHeaders(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {}

  @Override public void complete_MisplacedHeader(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {}

  @Override public void completeMisplacedHeader_IsEdition(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {}

  @Override public void completeMisplacedHeader_Name(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {}

  @Override public void completeEdition_Name(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeHeader(EDITION_2023_IN_QUOTES.toString(), context, acceptor);
    proposeHeader(EDITION_2024_IN_QUOTES.toString(), context, acceptor);
  }

  @Override public void complete_Edition(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (canStartHeader(context)) {
      proposeHeader(EDITION + space() + EQUAL_EDITION_2023_IN_QUOTES, context, acceptor);
      proposeHeader(EDITION + space() + EQUAL_EDITION_2024_IN_QUOTES, context, acceptor);
    }
  }

  private void proposeHeader(String text, ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(text, imageHelper.getImage(images.imageFor(SYNTAX)), context, acceptor);
  }

  private boolean canStartHeader(ContentAssistContext context) {
    Protobuf root = EcoreUtil2.getContainerOfType(context.getCurrentModel(), Protobuf.class);
    if (root == null && !context.getResource().getContents().isEmpty()
        && context.getResource().getContents().get(0) instanceof Protobuf) {
      root = (Protobuf) context.getResource().getContents().get(0);
    }
    if (root == null || root.getSyntax() != null || root.getEdition() != null
        || !root.getMisplacedHeaders().isEmpty()) return false;
    int start = context.getOffset() - context.getPrefix().length();
    for (EObject element : root.getElements()) {
      INode node = NodeModelUtils.getNode(element);
      if (node != null && node.getOffset() < start) return false;
    }
    return true;
  }

  @Override public void completeSyntax_Name(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(PROTO2_IN_QUOTES, context, acceptor);
    proposeAndAccept(PROTO3_IN_QUOTES, context, acceptor);
  }

  @Override public void complete_Syntax(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (!canStartHeader(context)) return;
    String proposal = SYNTAX + space() + EQUAL_PROTO2_IN_QUOTES;
    proposeAndAccept(proposal, imageHelper.getImage(images.imageFor(SYNTAX)), context, acceptor);

    proposal = SYNTAX + space() + EQUAL_PROTO3_IN_QUOTES;
    proposeAndAccept(proposal, imageHelper.getImage(images.imageFor(SYNTAX)), context, acceptor);
  }

  @Override public void complete_ID(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {}

  @Override public void complete_CHUNK(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {}

  @Override public void completeKeyword(Keyword keyword, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (keyword == null || !isKeywordAllowed(keyword, context)) {
      return;
    }
    AbstractRule rule = EcoreUtil2.getContainerOfType(keyword, AbstractRule.class);
    String ruleName = rule == null ? "" : rule.getName();
    // A reserved word in Name is an identifier, not a declaration/modifier proposal.
    if ("SafeReservedWord".equals(ruleName) || "ReservedWord".equals(ruleName)) {
      super.completeKeyword(keyword, context, acceptor);
      return;
    }
    if ("Edition".equals(ruleName) && "edition".equals(keyword.getValue())) {
      complete_Edition(context.getCurrentModel(), null, context, acceptor);
      return;
    }
    if ("=".equals(keyword.getValue()) && context.getLastCompleteNode() != null
        && isKeyword(context.getLastCompleteNode().getGrammarElement(), SYNTAX)) {
      proposeEqualProto2(context, acceptor);
      proposeEqualProto3(context, acceptor);
      return;
    }
    if ("=".equals(keyword.getValue()) && context.getLastCompleteNode() != null
        && isKeyword(context.getLastCompleteNode().getGrammarElement(), EDITION)) {
      proposeHeader(EQUAL_EDITION_2023_IN_QUOTES.toString(), context, acceptor);
      proposeHeader(EQUAL_EDITION_2024_IN_QUOTES.toString(), context, acceptor);
      return;
    }
    if ("[".equals(keyword.getValue()) && "MessageField".equals(ruleName)
        && proposeOpeningBracket(context, acceptor)) {
      return;
    }
    super.completeKeyword(keyword, context, acceptor);
  }

  private boolean isKeywordAllowed(Keyword keyword, ContentAssistContext context) {
    AbstractRule rule = EcoreUtil2.getContainerOfType(keyword, AbstractRule.class);
    String ruleName = rule == null ? "" : rule.getName();
    String word = keyword.getValue();
    if ("SafeReservedWord".equals(ruleName) || "ReservedWord".equals(ruleName)) return true;
    if ("MisplacedHeader".equals(ruleName)) return false;
    if (("Syntax".equals(ruleName) && "syntax".equals(word))
        || ("Edition".equals(ruleName) && "edition".equals(word))) return canStartHeader(context);
    if ("OptionImport".equals(ruleName) && "option".equals(word)) {
      return syntaxes.isEdition2024(semanticContext(context));
    }
    if ("WeakImport".equals(ruleName) && "weak".equals(word)
        && syntaxes.isEdition2024(semanticContext(context))) return false;
    if ("Visibility".equals(ruleName)) {
      EObject declaration = visibilityDeclaration(context);
      if (!syntaxes.isEdition2024(semanticContext(context)) || declaration == null) return false;
      if (!"export".equals(word)) return true;
      INode node = NodeModelUtils.getNode(declaration);
      if ((declaration instanceof Message || declaration instanceof Enum) && node != null
          && node.getOffset() >= context.getOffset() - context.getPrefix().length()) {
        return editionValidator.allowsExport(declaration);
      }
      return editionValidator.allowsExport(declaration, false) || editionValidator.allowsExport(declaration, true);
    }
    EObject model = semanticContext(context);
    if ("Modifier".equals(ruleName)) {
      MessageField field = enclosing(context, MessageField.class);
      return enclosing(context, OneOf.class) == null
          && (field == null || !(field.getType() instanceof MapTypeLink))
          && !(syntaxes.isEditions(model) && ("optional".equals(word) || "required".equals(word)))
          && !("required".equals(word) && syntaxes.isProto3(model));
    }
    if ("OneOf".equals(ruleName) && "repeated".equals(word)) {
      return false;
    }
    if ("MapType".equals(ruleName) && "map".equals(word)) {
      return enclosing(context, OneOf.class) == null && enclosing(context, TypeExtension.class) == null
          && mapPosition(context) == null;
    }
    if ("ScalarType".equals(ruleName) && mapKeyPosition(context)) {
      return !"double".equals(word) && !"float".equals(word) && !"bytes".equals(word);
    }
    if (("Group".equals(ruleName) && "group".equals(word)
        || "Extensions".equals(ruleName) && "extensions".equals(word)
        || "Stream".equals(ruleName) && "stream".equals(word)) && syntaxes.isProto3(model)) {
      return false;
    }
    if (syntaxes.isEditions(model) && ("Group".equals(ruleName) && "group".equals(word)
        || "Stream".equals(ruleName) && "stream".equals(word))) return false;
    if ("Extensions".equals(ruleName) && "extensions".equals(word)
        && enclosing(context, OneOf.class) != null) {
      return false;
    }
    if ("DefaultValueFieldOption".equals(ruleName) && "default".equals(word)) {
      return messageFields.supportsExplicitDefault(enclosing(context, MessageField.class));
    }
    if ("JsonNameFieldOption".equals(ruleName) && "json_name".equals(word)) {
      return enclosing(context, MessageField.class) != null && enclosing(context, TypeExtension.class) == null;
    }
    return true;
  }

  private EObject visibilityDeclaration(ContentAssistContext context) {
    int start = context.getOffset() - context.getPrefix().length();
    for (ILeafNode leaf : context.getResource().getParseResult().getRootNode().getLeafNodes()) {
      if (leaf.isHidden() || leaf.getEndOffset() <= start) continue;
      if ("}".equals(leaf.getText())) break;
      EObject next = NodeModelUtils.findActualSemanticObjectFor(leaf);
      EObject declaration = next;
      while (declaration != null && !(declaration instanceof Message) && !(declaration instanceof Enum)) {
        if (declaration instanceof MessageField || declaration instanceof OneOf
            || declaration instanceof Service || declaration instanceof AbstractOption) return null;
        declaration = declaration.eContainer();
      }
      INode node = declaration == null ? null : NodeModelUtils.getNode(declaration);
      return node != null && node.getOffset() >= start ? declaration : null;
    }
    Message message = enclosing(context, Message.class);
    return message == null ? EcoreUtil2.getContainerOfType(semanticContext(context), Protobuf.class) : message;
  }

  private <T extends EObject> T enclosing(ContentAssistContext context, Class<T> type) {
    T result = containingAtCaret(context.getCurrentModel(), type, context);
    T candidate = containingAtCaret(semanticContext(context), type, context);
    if (candidate != null && (result == null
        || NodeModelUtils.getNode(candidate).getTotalLength() < NodeModelUtils.getNode(result).getTotalLength())) {
      result = candidate;
    }
    return result == null ? containingAtCaret(context.getPreviousModel(), type, context) : result;
  }

  private <T extends EObject> T containingAtCaret(EObject model, Class<T> type, ContentAssistContext context) {
    T result = EcoreUtil2.getContainerOfType(model, type);
    INode node = result == null ? null : NodeModelUtils.getNode(result);
    return node != null && node.getTotalOffset() <= context.getOffset()
        && context.getOffset() <= node.getTotalEndOffset() ? result : null;
  }

  // The completion parser stops before an assignment and can leave currentModel at its owner
  // (or even at the previous range). The document's node model retains the actual source context.
  private EObject semanticContext(ContentAssistContext context) {
    INode node = nodeBeforeCaret(context);
    return node == null ? context.getCurrentModel() : NodeModelUtils.findActualSemanticObjectFor(node);
  }

  private INode nodeBeforeCaret(ContentAssistContext context) {
    INode root = context.getResource().getParseResult().getRootNode();
    int offset = Math.max(root.getOffset(), context.getOffset() - context.getPrefix().length() - 1);
    return NodeModelUtils.findLeafNodeAtOffset(context.getResource().getParseResult().getRootNode(), offset);
  }

  private String mapPosition(ContentAssistContext context) {
    return context == proposalContext ? proposalMapPosition : calculateMapPosition(context);
  }

  private String calculateMapPosition(ContentAssistContext context) {
    proposalStatement = null;
    proposalLastToken = null;
    proposalRangeOptions = false;
    proposalRangeRoot = false;
    proposalRangeValue = false;
    proposalRangeOption = null;
    // TypeLink's syntactic predicate rejects an incomplete map before it creates any MapType
    // model/node. Read visible protobuf tokens, not the recovered ComplexTypeLink named "map".
    boolean map = false;
    int depth = 0;
    int braceDepth = 0;
    int rangeBraceDepth = -1;
    int rangeBracketDepth = 0;
    String position = null;
    for (ILeafNode leaf : context.getResource().getParseResult().getRootNode().getLeafNodes()) {
      if (leaf.getOffset() >= context.getOffset()) {
        break;
      }
      if (leaf.isHidden()) {
        continue;
      }
      String token = leaf.getText();
      boolean brace = "{".equals(token) || "}".equals(token);
      if ("{".equals(token)) {
        braceDepth++;
      } else if ("}".equals(token)) {
        braceDepth--;
      }
      if (!proposalRangeOptions && (brace || ";".equals(token))) {
        proposalStatement = null;
      } else if (proposalStatement == null && !brace && !";".equals(token)) {
        proposalStatement = token;
      }
      if ("extensions".equals(proposalStatement) && "[".equals(token) && !proposalRangeOptions) {
        proposalRangeOptions = true;
        proposalRangeValue = false;
        rangeBraceDepth = braceDepth;
        rangeBracketDepth = 1;
      } else if (proposalRangeOptions) {
        if ("[".equals(token)) {
          rangeBracketDepth++;
        } else if ("]".equals(token) && --rangeBracketDepth == 0) {
          proposalRangeOptions = false;
        } else if (braceDepth == rangeBraceDepth && rangeBracketDepth == 1) {
          if ("=".equals(token)) {
            proposalRangeValue = true;
            proposalRangeOption = proposalLastToken;
          } else if (",".equals(token)) {
            proposalRangeValue = false;
          }
        }
      }
      proposalLastToken = token;
      if (grammar.getMapTypeAccess().getLessThanSignKeyword_1().getValue().equals(token) && map) {
        depth++;
        position = "key";
      } else if (depth > 0) {
        if (grammar.getMapTypeAccess().getGreaterThanSignKeyword_5().getValue().equals(token)) {
          if (--depth == 0) {
            position = null;
          }
        } else if (grammar.getMapTypeAccess().getCommaKeyword_3().getValue().equals(token) && depth == 1) {
          position = "value";
        } else if (";".equals(token) || "{".equals(token) || "}".equals(token)) {
          depth = 0;
          position = null;
        }
      }
      map = grammar.getMapTypeAccess().getMapKeyword_0().getValue().equals(token);
    }
    proposalRangeRoot = proposalRangeOptions && braceDepth == rangeBraceDepth && rangeBracketDepth == 1;
    proposalRangeAggregateDepth = proposalRangeOptions ? braceDepth - rangeBraceDepth : 0;
    return position;
  }

  private boolean mapKeyPosition(ContentAssistContext context) {
    return "key".equals(mapPosition(context));
  }

  private boolean isKeyword(EObject object, CommonKeyword keyword) {
    return object instanceof Keyword && keyword.hasValue(((Keyword) object).getValue());
  }

  private void proposeEqualProto2(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(EQUAL_PROTO2_IN_QUOTES, context, acceptor);
  }

  private void proposeEqualProto3(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(EQUAL_PROTO3_IN_QUOTES, context, acceptor);
  }

  private void proposeAndAccept(CompoundElement proposalText, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(proposalText.toString(), context, acceptor);
  }

  private EObject expectedType(ContentAssistContext context) {
    EObject model = semanticContext(context);
    DefaultValueFieldOption option = enclosing(context, DefaultValueFieldOption.class);
    if (option != null && !messageFields.supportsExplicitDefault(enclosing(context, MessageField.class))) {
      return null;
    }
    if (context.getCurrentModel() instanceof ValueField) {
      return optionValues.expectedType(context.getCurrentModel());
    }
    ValueField field = enclosing(context, ValueField.class);
    if (field != null) {
      return optionValues.expectedType(field);
    }
    AbstractOption assignment = enclosing(context, AbstractOption.class);
    return optionValues.expectedType(assignment == null ? model : assignment);
  }

  private boolean proposeOpeningBracket(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    EObject model = context.getCurrentModel();
    if (model instanceof ComplexValue) {
      return true;
    }
    if (model instanceof MessageField) {
      MessageField field = (MessageField) model;
      if (messageFields.supportsExplicitDefault(field)) {
        CompoundElement display = DEFAULT_EQUAL_IN_BRACKETS;
        int cursorPosition = display.indexOf(CLOSING_BRACKET);
        if (messageFields.isString(field)) {
          display = DEFAULT_EQUAL_STRING_IN_BRACKETS;
          cursorPosition++;
        }
        createAndAccept(display, cursorPosition, context, acceptor);
      } else {
        proposeAndAccept("[", context, acceptor);
      }
      return true;
    }
    return false;
  }

  private <T> T extractElementFromContext(ContentAssistContext context, Class<T> type) {
    EObject model = context.getCurrentModel();
    // this is most likely a bug in Xtext:
    if (!type.isInstance(model)) {
      model = context.getPreviousModel();
    }
    if (!type.isInstance(model)) {
      return null;
    }
    return type.cast(model);
  }

  private ICompletionProposal createCompletionProposal(CompoundElement proposal, ContentAssistContext context) {
    return createCompletionProposal(proposal.toString(), context);
  }

  @Override public void completeLiteral_Index(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (!(model instanceof Literal)) return;
    long index = literals.calculateNewIndexOf((Literal) model);
    if (index != Long.MIN_VALUE) {
      proposeIndex(index, context, acceptor);
    }
  }

  @Override public void completeLiteralLink_Target(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  @Override public void completeMessageField_Index(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (!(model instanceof MessageField)) return;
    long index = indexedElements.calculateNewIndexFor((MessageField) model);
    if (index != -1L) {
      proposeIndex(index, context, acceptor);
    }
  }

  private void proposeIndex(long index, ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeAndAccept(valueOf(index), context, acceptor);
  }

  @Override public void completeMessageField_Name(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeReservedNames(context, acceptor);
    MessageField field = extractElementFromContext(context, MessageField.class);
    if (field == null) {
      return;
    }
    TypeLink link = field.getType();
    boolean map = link instanceof MapTypeLink;
    if (map) {
      MapType target = ((MapTypeLink) link).getTarget();
      link = target == null ? null : target.getValueType();
    }
    String typeName = null;
    if (link instanceof ScalarTypeLink && ((ScalarTypeLink) link).getTarget() != null) {
      typeName = ((ScalarTypeLink) link).getTarget().getName();
    } else if (link instanceof ComplexTypeLink) {
      EObject target = ((ComplexTypeLink) link).getTarget();
      QualifiedName name = target == null || target.eIsProxy() ? null : qualifiedNames.getFullyQualifiedName(target);
      typeName = name == null ? null : name.getLastSegment();
    }
    if (typeName == null || typeName.isEmpty()) {
      return;
    }
    String base = toFirstLower(typeName) + (map ? "Map" : "");
    Set<String> used = new HashSet<String>();
    Message owner = EcoreUtil2.getContainerOfType(field, Message.class);
    if (owner != null) {
      for (MessageField sibling : messageFields.fieldsOf(owner)) {
        if (sibling != field) {
          used.add(sibling.getName());
        }
      }
      for (EObject sibling : owner.getElements()) {
        Object name = sibling.eClass().getEStructuralFeature("name") == null ? null
            : sibling.eGet(sibling.eClass().getEStructuralFeature("name"));
        if (sibling != field && name instanceof String) {
          used.add((String) name);
        }
      }
    }
    int index = map ? 0 : 1;
    String name = map ? base : base + index;
    while (used.contains(name)) {
      name = base + (++index);
    }
    proposeAndAccept(name, context, acceptor);
  }

  private void proposeReservedNames(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    for (Keyword keyword : EcoreUtil2.getAllContentsOfType(grammar.getReservedWordRule(), Keyword.class)) {
      proposeAndAccept(keyword.getValue(), context, acceptor);
    }
    for (Keyword keyword : EcoreUtil2.getAllContentsOfType(grammar.getSafeReservedWordRule(), Keyword.class)) {
      proposeAndAccept(keyword.getValue(), context, acceptor);
    }
  }

  private void proposeAndAccept(String proposalText, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    acceptor.accept(createCompletionProposal(proposalText, context));
  }

  @Override protected ICompletionProposal createCompletionProposal(String proposalText, ContentAssistContext context) {
    return createCompletionProposal(proposalText, null, defaultImage(), getPriorityHelper().getDefaultPriority(),
        context.getPrefix(), context);
  }

  private Image defaultImage() {
    return imageHelper.getImage(images.defaultImage());
  }

  @Override public void completeDefaultValueFieldOption_Value(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    MessageField field = enclosing(context, MessageField.class);
    if (messageFields.supportsExplicitDefault(field)) {
      proposeFieldValue(optionValues.typeOf(field), context, acceptor);
    }
  }

  private void proposeEmptyString(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    createAndAccept(EMPTY_STRING, 1, context, acceptor);
  }

  private void createAndAccept(CompoundElement display, int cursorPosition, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    ICompletionProposal proposal = createCompletionProposal(display, context);
    if (proposal instanceof ConfigurableCompletionProposal) {
      ConfigurableCompletionProposal configurable = (ConfigurableCompletionProposal) proposal;
      configurable.setCursorPosition(cursorPosition);
    }
    acceptor.accept(proposal);
  }

  @Override protected void lookupCrossReference(final CrossReference reference, final ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    final AbstractRule rule = EcoreUtil2.getContainerOfType(reference, AbstractRule.class);
    final String ruleName = rule == null ? "" : rule.getName();
    if ("NativeOptionSource".equals(ruleName)) {
      proposeNativeOptions(context, acceptor);
      return;
    }
    if ("MessageOptionField".equals(ruleName)) {
      proposeMembers(optionPathParentType(context), false, context, acceptor);
      return;
    }
    if ("NormalFieldName".equals(ruleName)) {
      ComplexValue aggregate = enclosing(context, ComplexValue.class);
      proposeMembers(optionValues.expectedType(aggregate), false, context, acceptor);
      return;
    }
    if ("LiteralLink".equals(ruleName)) {
      proposeFieldValue(expectedType(context), context, acceptor);
      return;
    }
    final EObject extensionType;
    if ("OptionSource".equals(ruleName)) {
      extensionType = nativeOptionsMessage(context);
    } else if ("ExtensionOptionField".equals(ruleName)) {
      extensionType = optionPathParentType(context);
    } else if ("ExtensionFieldName".equals(ruleName)) {
      extensionType = optionValues.expectedType(enclosing(context, ComplexValue.class));
    } else {
      extensionType = null;
    }
    lookupCrossReference(reference, context, acceptor, new Predicate<IEObjectDescription>() {
      @Override public boolean apply(IEObjectDescription description) {
        EObject target = EcoreUtil.resolve(description.getEObjectOrProxy(), context.getResource());
        if ("OptionSource".equals(ruleName) || "ExtensionOptionField".equals(ruleName)
            || "ExtensionFieldName".equals(ruleName)) {
          if (extensionType == null || !(target instanceof IndexedElement)
              || !(target.eContainer() instanceof TypeExtension)) {
            return false;
          }
          TypeExtension extension = (TypeExtension) target.eContainer();
          if (extension.getType() == null || extension.getType().getTarget() != extensionType) {
            return false;
          }
          if (!allowsOption((IndexedElement) target, null, false, context)) return false;
        }
        if ("ComplexTypeLink".equals(ruleName)) {
          if (mapKeyPosition(context)) {
            return false;
          }
          if (target instanceof Enum && syntaxes.isProto3(semanticContext(context))
              && Boolean.FALSE.equals(features.isOpenEnum((Enum) target))) {
            return false;
          }
        }
        if ("ExtensibleTypeLink".equals(ruleName) && syntaxes.isProto3(semanticContext(context))) {
          return isOptionExtendee(target);
        }
        return !(target instanceof IndexedElement) || !alreadyAssigned((IndexedElement) target, context);
      }
    });
  }

  private boolean isOptionExtendee(EObject target) {
    QualifiedName qualifiedName = target instanceof Message ? qualifiedNames.getFullyQualifiedName(target) : null;
    if (qualifiedName == null || qualifiedName.getSegmentCount() < 2) {
      return false;
    }
    String name = qualifiedName.getLastSegment();
    String namespace = qualifiedName.skipLast(1).toString();
    if (!"google.protobuf".equals(namespace) && !"proto2".equals(namespace)) {
      return false;
    }
    boolean optionsMessage = "FileOptions".equals(name) || "MessageOptions".equals(name)
        || "FieldOptions".equals(name) || "EnumOptions".equals(name) || "EnumValueOptions".equals(name)
        || "ServiceOptions".equals(name) || "MethodOptions".equals(name) || "OneofOptions".equals(name)
        || "ExtensionRangeOptions".equals(name);
    if (optionsMessage) {
      for (EObject element : ((Message) target).getElements()) {
        if (element instanceof Extensions && !((Extensions) element).getRanges().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean alreadyAssigned(IndexedElement target, ContentAssistContext context) {
    if (optionValues.isRepeated(target)) {
      return false;
    }
    List<IndexedElement> featurePath = memberFeaturePath(target, context);
    if (!featurePath.isEmpty() && !(optionValues.typeOf(target) instanceof Message)) {
      EObject source = valueSource(context);
      for (FeatureSets.Assignment assignment : features.assignmentsIn(nativeOptionOwner(context))) {
        if (assignment.source != source && assignment.path.equals(featurePath)) return true;
      }
    }
    ValueField valueField = enclosing(context, ValueField.class);
    ComplexValue aggregate = valueField == null ? enclosing(context, ComplexValue.class)
        : EcoreUtil2.getContainerOfType(valueField.eContainer(), ComplexValue.class);
    if (aggregate != null) {
      for (EObject sibling : aggregate.eContents()) {
        if (sibling instanceof ValueField && sibling != valueField
            && optionValues.declarationOf(sibling) == target) {
          return true;
        }
      }
      return false;
    }
    AbstractOption current = enclosing(context, AbstractOption.class);
    EObject owner = current == null ? nativeOptionOwner(context) : current.eContainer();
    if (owner == null) {
      return false;
    }
    for (EObject sibling : owner.eContents()) {
      if (!(sibling instanceof AbstractOption) || sibling == current) {
        continue;
      }
      AbstractOption option = (AbstractOption) sibling;
      if (options.sourceOf(option) == target
          && (current == null || options.fieldsOf(current).isEmpty() && options.fieldsOf(option).isEmpty()
              || current != null && options.rootSourceOf(current) == options.rootSourceOf(option)
                  && sameParentPath(current, option))) {
        return true;
      }
    }
    return false;
  }

  private boolean sameParentPath(AbstractOption first, AbstractOption second) {
    if (options.fieldsOf(first).size() != options.fieldsOf(second).size()) {
      return false;
    }
    for (int i = 0; i + 1 < options.fieldsOf(first).size(); i++) {
      if (options.fieldsOf(first).get(i).getTarget() != options.fieldsOf(second).get(i).getTarget()) {
        return false;
      }
    }
    return true;
  }

  private EObject optionPathParentType(ContentAssistContext context) {
    AbstractOption option = enclosing(context, AbstractOption.class);
    IndexedElement parent = options.rootSourceOf(option);
    int replacementStart = context.getOffset() - context.getPrefix().length();
    for (OptionField field : options.fieldsOf(option)) {
      INode node = NodeModelUtils.getNode(field);
      if (node == null || node.getEndOffset() >= replacementStart) {
        break;
      }
      parent = field.getTarget();
    }
    return optionValues.typeOf(parent);
  }

  private EObject nativeOptionOwner(ContentAssistContext context) {
    AbstractOption option = enclosing(context, AbstractOption.class);
    if (option != null) {
      return option.eContainer();
    }
    for (EObject current = semanticContext(context); current != null; current = current.eContainer()) {
      if ("option".equals(proposalStatement) && current instanceof MessageField
          && ((MessageField) current).getName() == null) {
        continue;
      }
      if (current instanceof MessageField || current instanceof Group || current instanceof Literal
          || current instanceof Extensions || current instanceof OneOf || current instanceof Message
          || current instanceof Enum || current instanceof Rpc || current instanceof Stream
          || current instanceof Service || current instanceof Protobuf) {
        return current;
      }
    }
    return null;
  }

  private Message nativeOptionsMessage(ContentAssistContext context) {
    EObject owner = nativeOptionOwner(context);
    String name = context == proposalContext && proposalRangeOptions ? "ExtensionRangeOptions"
        : owner instanceof Extensions ? "ExtensionRangeOptions"
        : owner instanceof Literal ? "EnumValueOptions"
        : owner instanceof Group && enclosing(context, Option.class) != null ? "MessageOptions"
        : owner instanceof MessageField || owner instanceof Group ? "FieldOptions"
        : owner instanceof OneOf ? "OneofOptions" : owner instanceof Message ? "MessageOptions"
        : owner instanceof Enum ? "EnumOptions" : owner instanceof Rpc ? "MethodOptions"
        : owner instanceof Stream ? "StreamOptions" : owner instanceof Service ? "ServiceOptions"
        : owner instanceof Protobuf ? "FileOptions" : null;
    if (name == null) {
      return null;
    }
    Resource resource = context.getResource();
    ProtoDescriptorProvider.ProtoDescriptorInfo descriptor = descriptorProvider.primaryDescriptor(resource);
    if (descriptor == null || descriptor.location == null) return null;
    Resource selected;
    try {
      selected = resource.getResourceSet().getResource(descriptor.location, true);
    } catch (RuntimeException exception) {
      // An unavailable authoritative descriptor must not fall back to unrelated indexed fields.
      return null;
    }
    if (selected != null && !selected.getContents().isEmpty()) {
      for (EObject declaration : selected.getContents().get(0).eContents()) {
        if (declaration instanceof Message && name.equals(((Message) declaration).getName())) {
          return (Message) declaration;
        }
      }
    }
    return null;
  }

  private void proposeNativeOptions(ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeMembers(nativeOptionsMessage(context), true, context, acceptor);
  }

  private void proposeMembers(EObject type, boolean nativeRoot, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (type instanceof Message) {
      for (MessageField field : messageFields.fieldsOf((Message) type)) {
        proposeMember(field, nativeRoot, context, acceptor);
      }
      for (EObject element : type.eContents()) {
        if (element instanceof Group) {
          proposeMember((Group) element, nativeRoot, context, acceptor);
        }
      }
    } else if (type instanceof Group) {
      for (EObject element : type.eContents()) {
        if (element instanceof IndexedElement) {
          proposeMember((IndexedElement) element, nativeRoot, context, acceptor);
        }
      }
    } else if (type instanceof MapType) {
      for (MessageField field : ((MapType) type).getEntryFields()) {
        proposeMember(field, nativeRoot, context, acceptor);
      }
    }
  }

  private void proposeMember(IndexedElement field, boolean nativeRoot, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    String name = options.nameForOption(field);
    if (name == null || alreadyAssigned(field, context) || !allowsOption(field, null, nativeRoot, context)) {
      return;
    }
    proposeAndAccept(name, context, acceptor);
  }

  // Use linked declaration paths; node positions only identify the component being replaced.
  private List<IndexedElement> memberFeaturePath(IndexedElement target, ContentAssistContext context) {
    AbstractOption option = enclosing(context, AbstractOption.class);
    if (option == null || !features.isFeaturesRoot(options.rootSourceOf(option), option)) {
      return Collections.emptyList();
    }
    List<IndexedElement> path = new ArrayList<IndexedElement>();
    ComplexValue aggregate = enclosing(context, ComplexValue.class);
    if (aggregate != null) {
      path.addAll(features.pathOf(aggregate));
    } else {
      int replacementStart = context.getOffset() - context.getPrefix().length();
      for (OptionField field : options.fieldsOf(option)) {
        INode node = NodeModelUtils.getNode(field);
        if (node == null || node.getEndOffset() >= replacementStart) break;
        if (field.getTarget() == null || field.getTarget().eIsProxy()) return Collections.emptyList();
        path.add(field.getTarget());
      }
    }
    path.add(target);
    return path;
  }

  private EObject valueSource(ContentAssistContext context) {
    ValueField field = enclosing(context, ValueField.class);
    return field == null ? enclosing(context, AbstractOption.class) : field;
  }

  private boolean allowsOption(IndexedElement declaration, Literal value, boolean nativeRoot,
      ContentAssistContext context) {
    EObject owner = nativeOptionOwner(context);
    EObject source = valueSource(context);
    if (nativeRoot) {
      String name = options.nameForOption(declaration);
      if ("map_entry".equals(name)) return false;
      if ("packed".equals(name) && (syntaxes.isEditions(owner)
          || !messageFields.isPackable(enclosing(context, MessageField.class)))) return false;
      if (features.isFeaturesRoot(declaration, owner)) {
        return syntaxes.isEditions(owner) && features.hasRequiredMetadata(owner);
      }
    }
    List<IndexedElement> path = memberFeaturePath(declaration, context);
    if (!path.isEmpty()) return features.canAssign(owner, path, value);
    return features.allowsTarget(declaration, owner, source == null ? owner : source)
        && features.supportError(declaration, value, owner) == null;
  }

  private boolean allowsValue(Literal value, ContentAssistContext context) {
    EObject source = valueSource(context);
    List<IndexedElement> path = features.pathOf(source);
    EObject owner = nativeOptionOwner(context);
    if (!path.isEmpty()) return features.canAssign(owner, path, value);
    IndexedElement declaration = optionValues.declarationOf(source);
    AbstractOption option = enclosing(context, AbstractOption.class);
    if (option != null && options.isNative(option) && declaration == options.rootSourceOf(option)
        && !allowsOption(declaration, value, true, context)) return false;
    return declaration == null || features.allowsTarget(declaration, owner, source)
        && features.supportError(declaration, value, owner) == null;
  }

  @Override public void completeNativeOptionSource_Target(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeNativeOptions(context, acceptor);
  }

  @Override public void completeCustomOption_Value(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  @Override public void completeCustomFieldOption_Value(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  @Override public void completeNativeOption_Value(EObject model, Assignment assignment, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  @Override public void completeNativeFieldOption_Value(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  @Override public void completeJsonNameFieldOption_Value(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeEmptyString(context, acceptor);
  }

  @Override public void complete_Value(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    complete_SimpleValueLink(model, ruleCall, context, acceptor);
  }

  @Override public void complete_SimpleValueLink(EObject model, RuleCall ruleCall, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (enclosing(context, JsonNameFieldOption.class) != null) {
      proposeEmptyString(context, acceptor);
    } else {
      proposeFieldValue(expectedType(context), context, acceptor);
    }
  }

  @Override public void completeComplexValueField_Values(EObject model, Assignment assignment,
      ContentAssistContext context, ICompletionProposalAcceptor acceptor) {
    proposeFieldValue(expectedType(context), context, acceptor);
  }

  private void proposeFieldValue(EObject expectedType, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    if (!allowsValue(null, context)) return;
    if (expectedType instanceof ScalarTypeLink) {
      ScalarType scalar = ((ScalarTypeLink) expectedType).getTarget();
      if (scalar == ScalarType.BOOL) {
        proposeAndAccept("false", context, acceptor);
        IndexedElement declaration = optionValues.declarationOf(valueSource(context));
        AbstractOption option = enclosing(context, AbstractOption.class);
        if (!(syntaxes.isEditions(nativeOptionOwner(context)) && option != null && options.isNative(option)
            && declaration == options.rootSourceOf(option)
            && "java_string_check_utf8".equals(options.nameForOption(declaration)))) {
          proposeAndAccept("true", context, acceptor);
        }
      } else if (scalar == ScalarType.STRING || scalar == ScalarType.BYTES) {
        proposeEmptyString(context, acceptor);
      } else if (scalar == ScalarType.DOUBLE || scalar == ScalarType.FLOAT) {
        proposeAndAccept("0", context, acceptor);
        proposeAndAccept("inf", context, acceptor);
        proposeAndAccept("nan", context, acceptor);
      } else if (scalar != null) {
        proposeAndAccept("0", context, acceptor);
      }
    } else if (expectedType instanceof Enum) {
      Image image = imageHelper.getImage(images.imageFor(LITERAL));
      for (EObject element : ((Enum) expectedType).getElements()) {
        if (element instanceof Literal && ((Literal) element).getName() != null
            && allowsValue((Literal) element, context)) {
          proposeAndAccept(((Literal) element).getName(), image, context, acceptor);
        }
      }
    } else if (expectedType instanceof Message || expectedType instanceof Group || expectedType instanceof MapType) {
      ICompletionProposal proposal = createCompletionProposal("{}", context);
      if (proposal instanceof ConfigurableCompletionProposal) {
        ((ConfigurableCompletionProposal) proposal).setCursorPosition(1);
      }
      acceptor.accept(proposal);
    }
  }

  private void proposeAndAccept(String proposalText, Image image, ContentAssistContext context,
      ICompletionProposalAcceptor acceptor) {
    ICompletionProposal proposal = createCompletionProposal(proposalText, proposalText, image, context);
    acceptor.accept(proposal);
  }

  @Override public ICompletionProposal createCompletionProposal(String proposal, String displayString, Image image,
      ContentAssistContext contentAssistContext) {
    StyledString styled = null;
    if (displayString != null) {
      styled = new StyledString(displayString);
    }
    int priority = getPriorityHelper().getDefaultPriority();
    String prefix = contentAssistContext.getPrefix();
    return createCompletionProposal(proposal, styled, image, priority, prefix, contentAssistContext);
  }
}
