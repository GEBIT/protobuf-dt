/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.formatting;

import static com.google.eclipse.protobuf.grammar.CommonKeyword.CLOSING_BRACKET;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.CLOSING_CURLY_BRACKET;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.EQUAL;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.OPENING_BRACKET;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.OPENING_CURLY_BRACKET;
import static com.google.eclipse.protobuf.grammar.CommonKeyword.SEMICOLON;
import static com.google.eclipse.protobuf.util.CommonWords.space;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.google.eclipse.protobuf.services.ProtobufGrammarAccess;
import com.google.inject.Inject;
import com.google.eclipse.protobuf.protobuf.Protobuf;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.TypeRef;
import org.eclipse.xtext.formatting.impl.AbstractDeclarativeFormatter;
import org.eclipse.xtext.formatting.impl.FormattingConfig;
import org.eclipse.xtext.impl.TerminalRuleImpl;
import org.eclipse.xtext.parsetree.reconstr.ITokenStream;

/**
 * Provides custom formatting.
 *
 * @author alruiz@google.com (Alex Ruiz)
 *
 * @see <a href="http://www.eclipse.org/Xtext/documentation/2_0_0/105-formatting.php">Xtext Formatting</a>
 */
public class ProtobufFormatter extends AbstractDeclarativeFormatter {
	
  @Inject private IMaximumLineWidthProvider maximumLineWidthProvider;
  
  
  
    @Override
	protected synchronized FormattingConfig getConfig() {
		final FormattingConfig config = super.getConfig();
		
		// This can dynamically change at runtime
		config.setAutoLinewrap(maximumLineWidthProvider.maximumLineWidth());
		
		return config;
	}
	
  @Override protected void configureFormatting(FormattingConfig c) {
    ProtobufGrammarAccess g = (ProtobufGrammarAccess) getGrammarAccess();
    c.setLinewrap(0, 1, 2).before(g.getSL_COMMENTRule());
    c.setLinewrap(2).after(g.getSyntaxRule());
    c.setLinewrap(2).after(g.getEditionRule());
    c.setLinewrap(2).before(g.getPackageRule());
    c.setLinewrap(2).after(g.getPackageRule());
    c.setLinewrap(1,1,2).after(g.getNormalImportRule());
    c.setLinewrap(1,1,2).after(g.getPublicImportRule());
    c.setLinewrap(1,1,2).after(g.getWeakImportRule());
    c.setLinewrap(1,1,2).after(g.getOptionImportRule());
    c.setLinewrap(1,1,2).after(g.getNativeOptionRule());
    c.setLinewrap(1,1,2).after(g.getCustomOptionRule());
    c.setLinewrap(1,2,3).before(g.getMessageRule());
    c.setLinewrap(2).after(g.getMessageRule());
    c.setLinewrap(1).after(g.getMessageFieldRule());
    c.setLinewrap(2).after(g.getOneOfRule());
    c.setLinewrap(1).after(g.getGroupRule());
    c.setLinewrap(2).after(g.getEnumRule());
    c.setLinewrap(1).after(g.getEnumElementRule());
    c.setLinewrap(1).after(g.getExtensionsRule());
    c.setLinewrap(1).after(g.getReservedRule());
    c.setLinewrap(1).after(g.getReservedEnumRule());
    c.setLinewrap(1).after(g.getRpcRule());
    c.setLinewrap(2).after(g.getServiceRule());
    c.setLinewrap(1).after(g.getStreamRule());
    c.setSpace(space()).after(g.getVisibilityRule());

    c.setLinewrap(1).between(g.getCHUNKRule(), g.getCHUNKRule());
    c.setLinewrap(1).between(g.getML_COMMENTRule(), g.getCHUNKRule());
    c.setIndentationIncrement().before(g.getStringLiteralRule());
    c.setIndentationDecrement().after(g.getStringLiteralRule());

    for (Keyword k : g.findKeywords(EQUAL.toString())) {
      c.setSpace(space()).around(k);
    }
    for (Keyword k : g.findKeywords(SEMICOLON.toString())) {
      c.setNoSpace().before(k);
    }
    for (Keyword k : g.findKeywords(",")) {
      c.setNoSpace().before(k);
      c.setSpace(space()).after(k);
    }
    for (Keyword k : g.findKeywords(OPENING_CURLY_BRACKET.toString())) {
      c.setIndentationIncrement().after(k);
      c.setLinewrap(1).after(k);
    }
    for (Keyword k : g.findKeywords(CLOSING_CURLY_BRACKET.toString())) {
      c.setIndentationDecrement().before(k);
      c.setLinewrap(1,1,2).before(k);
    }    
    for (Keyword k : g.findKeywords(OPENING_BRACKET.toString(), "(")) {
      c.setNoSpace().after(k);
    }
    for (Keyword k : g.findKeywords(CLOSING_BRACKET.toString(), ")")) {
      c.setNoSpace().before(k);
    }
    c.setNoSpace().around(g.getMapTypeAccess().getLessThanSignKeyword_1());
    c.setNoSpace().before(g.getMapTypeAccess().getGreaterThanSignKeyword_5());
    c.setSpace(space()).after(g.getMapTypeAccess().getGreaterThanSignKeyword_5());
    c.setNoSpace().after(g.getAnyFieldNameAccess().getUrlPrefixAssignment_0_0_1());
  }
  
  @Override
	public ITokenStream createFormatterStream(EObject aContext, String anIndent, ITokenStream anOut,
			boolean aPreserveWhitespaces) {
		return super.createFormatterStream(aContext, anIndent, new FirstCommentIndentationFixingTokenStream(anOut,
				aContext == null || aContext instanceof Protobuf),
				aPreserveWhitespaces);
	}

	@Override
	public ITokenStream createFormatterStream(String anIndent, ITokenStream anOut, boolean aPreserveWhitespaces) {
		return super.createFormatterStream(anIndent, new FirstCommentIndentationFixingTokenStream(anOut,
				anIndent == null || anIndent.isEmpty()), aPreserveWhitespaces);
	}
  
  public class FirstCommentIndentationFixingTokenStream implements ITokenStream {

	private ITokenStream out;
	
	private final Set<EObject> openingCurlyBracketRuleCalls = new HashSet<>();
	
	private final TypeRef slCommentType;
			
	private boolean lastWasOpening;
	private final boolean documentStart;
	private boolean hasText;
	
	public FirstCommentIndentationFixingTokenStream(ITokenStream anOut, boolean documentStart) {
		out = anOut;
		this.documentStart = documentStart;
				
		ProtobufGrammarAccess g = (ProtobufGrammarAccess) getGrammarAccess();
		openingCurlyBracketRuleCalls.add(g.getMessageAccess().getLeftCurlyBracketKeyword_3());
		openingCurlyBracketRuleCalls.add(g.getOneOfAccess().getLeftCurlyBracketKeyword_3()); 
		
		slCommentType = g.getSL_COMMENTRule().getType();
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void writeHidden(EObject grammarElement, String value) throws IOException {
		// Rule-level before-layout does not introduce blank lines at the document boundary.
		if (documentStart && !hasText) {
			boolean whitespace = true;
			for (int i = 0; i < value.length(); i++) {
				if (!Character.isWhitespace(value.charAt(i))) {
					whitespace = false;
					break;
				}
			}
			if (whitespace) {
				return;
			}
			hasText = true;
		}
		// This is a weird-looking fix for an ugly problem/bug in XText declarative formatting
		// that I don't know how to fix otherwise: the indentation of comments right after an
		// indentation-increasing grammar element is messed up like this:
		// 
		// message AddFinancialTransaction {
		// // Mandatory: command header
		//  command.Header header = 1;
		//  // Purchase tx id.
		//  string purchase_transaction_id = 2;
		// 
		// The comment should be indented further. The code here fixes it by injecting additional
		// spaces when writing out stuff. Not the nicest fix, but seems to work fine.
		if(openingCurlyBracketRuleCalls.contains(grammarElement)) {
			lastWasOpening = true;
			out.writeHidden(grammarElement, value);		
		} else {		
			if(lastWasOpening) {
				if(grammarElement instanceof TerminalRuleImpl) {
					if(((TerminalRuleImpl) grammarElement).getType() == slCommentType) {				
						out.writeHidden(grammarElement, "  " + value);
						return;
					}
				} else {
					lastWasOpening = false;
				}
			}
			out.writeHidden(grammarElement, value);
		}
	}

	@Override
	public void writeSemantic(EObject grammarElement, String value) throws IOException {
		if (!value.isEmpty()) {
			hasText = true;
		}
		out.writeSemantic(grammarElement, value);
	}
	  
  }
  
}
