/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.resources;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.google.common.collect.Lists;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Parser for "values" files.
 *
 * This parses the file and returns a list of {@link Resource} object.
 */
class ValueResourceParser {

    private final File mFile;

    /**
     * Creates the parser for a given file.
     * @param file the file to parse.
     */
    ValueResourceParser(File file) {
        mFile = file;
    }

    /**
     * Parses the file and returns a list of {@link Resource} objects.
     * @return a list of resources.
     *
     * @throws IOException
     */
    @NonNull
    List<Resource> parseFile() throws IOException {
        Document document = parseDocument(mFile);

        // get the root node
        Node rootNode = document.getDocumentElement();
        if (rootNode == null) {
            return Collections.emptyList();
        }
        NodeList nodes = rootNode.getChildNodes();

        List<Resource> resources = Lists.newArrayListWithExpectedSize(nodes.getLength());

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Resource resource = getResource(node);
            if (resource != null) {
                resources.add(resource);
            }
        }

        return resources;
    }

    /**
     * Returns a new Resource object for a given node.
     * @param node the node representing the resource.
     * @return a Resource object or null.
     */
    static Resource getResource(Node node) {
        ResourceType type = getType(node);
        String name = getName(node);

        if (type != null && name != null) {
            return new Resource(name, type, node);
        }

        return null;
    }

    /**
     * Returns the type of the Resource based on a node's attributes.
     * @param node the node
     * @return the ResourceType or null if it could not be inferred.
     */
    static ResourceType getType(Node node) {
        String nodeName = node.getLocalName();
        String typeString = null;

        if (TAG_ITEM.equals(nodeName)) {
            Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_TYPE);
            if (attribute != null) {
                typeString = attribute.getValue();
            }
        } else {
            // the type is the name of the node.
            typeString = nodeName;
        }

        if (typeString != null) {
            return ResourceType.getEnum(typeString);
        }

        return null;
    }

    /**
     * Returns the name of the resource based a node's attributes.
     * @param node the node.
     * @return the name or null if it could not be inferred.
     */
    static String getName(Node node) {
        Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_NAME);

        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    /**
     * Loads the DOM for a given file and returns a {@link Document} object.
     * @param file the file to parse
     * @return a Document object.
     * @throws IOException
     */
    @NonNull
    static Document parseDocument(File file) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file));
        InputSource is = new InputSource(stream);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
