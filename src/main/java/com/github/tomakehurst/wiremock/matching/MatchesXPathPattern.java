/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.matching;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.tomakehurst.wiremock.common.SilentErrorHandler;
import com.github.tomakehurst.wiremock.common.Xml;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.custommonkey.xmlunit.NamespaceContext;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;

import static com.github.tomakehurst.wiremock.common.Exceptions.throwUnchecked;
import static com.github.tomakehurst.wiremock.common.LocalNotifier.notifier;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Sets.newTreeSet;

@JsonSerialize(using = XPathPatternJsonSerializer.class)
public class MatchesXPathPattern extends PathPattern {

    private final Map<String, String> xpathNamespaces;

    public MatchesXPathPattern(String xpath) {
        this(xpath, null, null);
    }

    public MatchesXPathPattern(String xpath, StringValuePattern valuePattern) {
        this(xpath, null, valuePattern);
    }

    public MatchesXPathPattern(String xpath, Map<String, String> namespaces) {
        this(xpath, namespaces, null);
    }

    public MatchesXPathPattern(@JsonProperty("matchesXPath") String xpath,
                               @JsonProperty("namespaces") Map<String, String> namespaces,
                               @JsonProperty("valuePattern") StringValuePattern valuePattern) {
        super(xpath, valuePattern);
        xpathNamespaces = namespaces == null || namespaces.isEmpty() ? null : namespaces;
    }

    public MatchesXPathPattern withXPathNamespace(String name, String namespaceUri) {
        Map<String, String> namespaceMap = ImmutableMap.<String, String>builder()
            .putAll(firstNonNull(xpathNamespaces, Collections.<String, String>emptyMap()))
            .put(name, namespaceUri)
            .build();
        return new MatchesXPathPattern(expectedValue, namespaceMap);
    }

    public String getMatchesXPath() {
        return expectedValue;
    }

    @JsonGetter("xPathNamespaces")
    public Map<String, String> getXPathNamespaces() {
        return xpathNamespaces;
    }

    @Override
    protected MatchResult isSimpleJsonPathMatch(String value) {
        if (value == null) {
            return MatchResult.noMatch();
        }

        NodeList nodeList = findXmlNodesMatching(value);

        return MatchResult.of(nodeList != null && nodeList.getLength() > 0);
    }

    @Override
    protected MatchResult isAdvancedJsonPathMatch(String value) {
        if (value == null) {
            return MatchResult.noMatch();
        }

        NodeList nodeList = findXmlNodesMatching(value);
        if (nodeList == null || nodeList.getLength() == 0) {
            return MatchResult.noMatch();
        }

        SortedSet<MatchResult> results = newTreeSet();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            String nodeValue = Xml.toStringValue(node);
            results.add(valuePattern.match(nodeValue));
        }

        return results.last();
    }

    private NodeList findXmlNodesMatching(String value) {
        try {
            DocumentBuilder documentBuilder = Xml.newDocumentBuilderFactory().newDocumentBuilder();
            documentBuilder.setErrorHandler(new SilentErrorHandler());
            Document inDocument = XMLUnit.buildDocument(documentBuilder, new StringReader(value));
            XpathEngine simpleXpathEngine = XMLUnit.newXpathEngine();
            if (xpathNamespaces != null) {
                NamespaceContext namespaceContext = new SimpleNamespaceContext(xpathNamespaces);
                simpleXpathEngine.setNamespaceContext(namespaceContext);
            }
            return simpleXpathEngine.getMatchingNodes(expectedValue, inDocument);
        } catch (SAXException e) {
            notifier().info(String.format(
                "Warning: failed to parse the XML document. Reason: %s\nXML: %s", e.getMessage(), value));
            return null;
        } catch (IOException e) {
            notifier().info(e.getMessage());
            return null;
        } catch (XpathException e) {
            notifier().info("Warning: failed to evaluate the XPath expression " + expectedValue);
            return null;
        } catch (Exception e) {
            return throwUnchecked(e, NodeList.class);
        }
    }
}
