/*-
 * #%L
 * This file is part of libtiled-java.
 * %%
 * Copyright (C) 2004 - 2020 Thorbjørn Lindeijer <thorbjorn@lindeijer.nl>
 * Copyright (C) 2004 - 2020 Adam Turk <aturk@biggeruniverse.com>
 * Copyright (C) 2016 - 2020 Mike Thomas <mikepthomas@outlook.com>
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mapeditor.io.xml;

import java.io.IOException;
import java.io.Writer;
import java.util.Stack;

/**
 * A simple helper class to write an XML file. Based on
 * http://www.xmlsoft.org/html/libxml-xmlwriter.html
 *
 * @deprecated
 * @version 1.4.2
 */
@Deprecated
public class XMLWriter {

	private boolean bIndent = true;
	private String indentString = " ";
	private String newLine = "\n";
	private final Writer w;

	private final Stack<String> openElements;
	private boolean bStartTagOpen;
	private boolean bDocumentOpen;

	/**
	 * Constructor for XMLWriter.
	 *
	 * @param writer a {@link java.io.Writer} object.
	 */
	public XMLWriter(Writer writer) {
		this.openElements = new Stack<>();
		this.w = writer;
	}

	/**
	 * endDocument.
	 *
	 * @throws java.io.IOException if any.
	 */
	public void endDocument() throws IOException {
		// End all open elements.
		while (!this.openElements.isEmpty()) {
			this.endElement();
		}

		this.w.flush(); // writers do not always flush automatically...
	}

	/**
	 * endElement.
	 *
	 * @throws java.io.IOException if any.
	 */
	public void endElement() throws IOException {
		String name = this.openElements.pop();

		// If start tag still open, end with />, else with </name>.
		if (this.bStartTagOpen) {
			this.w.write("/>" + this.newLine);
			this.bStartTagOpen = false;
		}
		else {
			this.writeIndent();
			this.w.write("</" + name + ">" + this.newLine);
		}

		// Set document closed when last element is closed
		if (this.openElements.isEmpty()) {
			this.bDocumentOpen = false;
		}
	}

	/**
	 * setIndent.
	 *
	 * @param bIndent a boolean.
	 */
	public void setIndent(boolean bIndent) {
		this.bIndent = bIndent;
		this.newLine = bIndent ? "\n" : "";
	}

	/**
	 * Setter for the field <code>indentString</code>.
	 *
	 * @param indentString a {@link java.lang.String} object.
	 */
	public void setIndentString(String indentString) {
		this.indentString = indentString;
	}

	/**
	 * startDocument.
	 *
	 * @throws java.io.IOException if any.
	 */
	public void startDocument() throws IOException {
		this.startDocument("1.0");
	}

	/**
	 * startDocument.
	 *
	 * @param version a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 */
	public void startDocument(String version) throws IOException {
		this.w.write("<?xml version=\"" + version + "\" encoding=\"UTF-8\"?>"
			+ this.newLine);
		this.bDocumentOpen = true;
	}

	/**
	 * startElement.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void startElement(String name)
		throws IOException, XMLWriterException {
		if (!this.bDocumentOpen) {
			throw new XMLWriterException(
				"Can't start new element, no open document.");
		}

		if (this.bStartTagOpen) {
			this.w.write(">" + this.newLine);
		}

		this.writeIndent();
		this.w.write("<" + name);

		this.openElements.push(name);
		this.bStartTagOpen = true;
	}

	/**
	 * writeAttribute.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a double.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeAttribute(String name, double content)
		throws IOException, XMLWriterException {
		// TODO: Tiled omits the decimals if it's '.0' so this is for parity
		long longContent = (long) content;
		if (longContent == content) {
			this.writeAttribute(name, String.valueOf(longContent));
		}
		else {
			this.writeAttribute(name, String.valueOf(content));
		}
	}

	/**
	 * <p>
	 * writeAttribute.
	 * </p>
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a float.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeAttribute(String name, float content)
		throws IOException, XMLWriterException {
		this.writeAttribute(name, String.valueOf(content));
	}

	/**
	 * writeAttribute.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a int.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeAttribute(String name, int content)
		throws IOException, XMLWriterException {
		this.writeAttribute(name, String.valueOf(content));
	}

	/**
	 * writeAttribute.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a long.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeAttribute(String name, long content)
		throws IOException, XMLWriterException {
		this.writeAttribute(name, String.valueOf(content));
	}

	/**
	 * writeAttribute.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeAttribute(String name, String content)
		throws IOException, XMLWriterException {
		if (this.bStartTagOpen) {
			String escapedContent =
				(content != null) ? content.replaceAll("\"", "&quot;") : "";
			this.w.write(" " + name + "=\"" + escapedContent + "\"");
		}
		else {
			throw new XMLWriterException(
				"Can't write attribute without open start tag.");
		}
	}

	/**
	 * writeCDATA.
	 *
	 * @param content a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 */
	public void writeCDATA(String content) throws IOException {
		if (this.bStartTagOpen) {
			this.w.write(">" + this.newLine);
			this.bStartTagOpen = false;
		}

		this.writeIndent();
		this.w.write(content + this.newLine);
	}

	/**
	 * writeComment.
	 *
	 * @param content a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 */
	public void writeComment(String content) throws IOException {
		if (this.bStartTagOpen) {
			this.w.write(">" + this.newLine);
			this.bStartTagOpen = false;
		}

		this.writeIndent();
		this.w.write("<!-- " + content + " -->" + this.newLine);
	}

	/**
	 * writeDocType.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param pubId a {@link java.lang.String} object.
	 * @param sysId a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeDocType(String name, String pubId, String sysId)
		throws IOException, XMLWriterException {
		if (!this.bDocumentOpen) {
			throw new XMLWriterException(
				"Can't write DocType, no open document.");
		}
		else if (!this.openElements.isEmpty()) {
			throw new XMLWriterException(
				"Can't write DocType, open elements exist.");
		}

		this.w.write("<!DOCTYPE " + name + " ");

		if (pubId != null) {
			this.w.write("PUBLIC \"" + pubId + "\"");
			if (sysId != null) {
				this.w.write(" \"" + sysId + "\"");
			}
		}
		else if (sysId != null) {
			this.w.write("SYSTEM \"" + sysId + "\"");
		}

		this.w.write(">" + this.newLine);
	}

	/**
	 * writeElement.
	 *
	 * @param name a {@link java.lang.String} object.
	 * @param content a {@link java.lang.String} object.
	 * @throws java.io.IOException if any.
	 * @throws org.mapeditor.io.xml.XMLWriterException if any.
	 */
	public void writeElement(String name, String content)
		throws IOException, XMLWriterException {
		this.startElement(name);
		this.writeCDATA(content);
		this.endElement();
	}

	private void writeIndent() throws IOException {
		if (this.bIndent) {
			for (String openElement : this.openElements) {
				this.w.write(this.indentString);
			}
		}
	}
}