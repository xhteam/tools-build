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

import com.android.resources.ResourceType;
import com.google.common.collect.Lists;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

/**
 */
class ValueResourceParser {

    private final File mFile;

    ValueResourceParser(File file) {
        mFile = file;
    }

    List<Resource> parseFile() throws FileNotFoundException {
        Document document = parseDocument();

        NodeList nodes = document.getChildNodes();

        // get the root node
        Node rootNode = nodes.item(0);
        nodes = rootNode.getChildNodes();

        List<Resource> resources = Lists.newArrayListWithExpectedSize(nodes.getLength());

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            ResourceType type = getType(node);
            String name = getName(node);

            if (type != null && name != null) {
                Resource r = new Resource(name, type, node);
                resources.add(r);
            }
        }

        return resources;
    }

    private ResourceType getType(Node node) {
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

    private String getName(Node node) {
        Attr attribute = (Attr) node.getAttributes().getNamedItemNS(null, ATTR_NAME);

        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    private Document parseDocument() throws FileNotFoundException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(mFile));
        InputSource is = new InputSource(stream);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (Exception e) {
            throw new BuildException(e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
            }
        }
    }
}
