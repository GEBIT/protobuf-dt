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

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
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
    c.setLinewrap(2).before(g.getPackageRule());
    c.setLinewrap(2).after(g.getPackageRule());
    c.setLinewrap(1,1,2).after(g.getNormalImportRule());
    c.setLinewrap(1,1,2).after(g.getPublicImportRule());
    c.setLinewrap(1,1,2).after(g.getWeakImportRule());
    c.setLinewrap(1,1,2).after(g.getNativeOptionRule());
    c.setLinewrap(1,1,2).after(g.getCustomOptionRule());
    c.setLinewrap(1,2,3).before(g.getMessageRule());
    c.setLinewrap(2).after(g.getMessageRule());
    c.setLinewrap(1).after(g.getMessageFieldRule());
    c.setLinewrap(1).after(g.getGroupRule());
    c.setLinewrap(2).after(g.getEnumRule());
    c.setLinewrap(1).after(g.getEnumElementRule());
    c.setLinewrap(1).after(g.getExtensionsRule());
    c.setLinewrap(1).after(g.getRpcRule());
    c.setLinewrap(2).after(g.getServiceRule());
    c.setLinewrap(1).after(g.getStreamRule());

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
  }
  
  @Override
	public ITokenStream createFormatterStream(EObject aContext, String anIndent, ITokenStream anOut,
			boolean aPreserveWhitespaces) {
		return super.createFormatterStream(aContext, anIndent, new FirstCommentIndentationFixingTokenStream(anOut),
				aPreserveWhitespaces);
	}

	@Override
	public ITokenStream createFormatterStream(String anIndent, ITokenStream anOut, boolean aPreserveWhitespaces) {
		return super.createFormatterStream(anIndent, new FirstCommentIndentationFixingTokenStream(anOut), aPreserveWhitespaces);
	}
  
  public class FirstCommentIndentationFixingTokenStream implements ITokenStream {

	private ITokenStream out;
	
	private final Set<EObject> openingCurlyBracketRuleCalls = new HashSet<>();
	
	private final TypeRef slCommentType;
			
	private boolean lastWasOpening;
	
	public FirstCommentIndentationFixingTokenStream(ITokenStream anOut) {
		out = anOut;
				
		ProtobufGrammarAccess g = (ProtobufGrammarAccess) getGrammarAccess();
		openingCurlyBracketRuleCalls.add(g.getMessageAccess().getLeftCurlyBracketKeyword_2());
		slCommentType = g.getSL_COMMENTRule().getType();
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void writeHidden(EObject grammarElement, String value) throws IOException {
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
		out.writeSemantic(grammarElement, value);
	}
	  
  }
  
}
