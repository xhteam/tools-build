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

import com.android.SdkConstants;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.sun.xml.internal.rngom.ast.builder.BuildException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Represents a set of resources at the same level (not overlay) coming from different sources
 * (folders or resource bundles)
 */
public class ResourceSet {

    /**
     * Resource files
     */
    //private final List<ResourceFile> mFiles = Lists.newArrayList();

    /**
     * The key is the {@link com.android.builder.resources.Resource#getKey()}.
     */
    private final Map<String, Resource> mItems = Maps.newHashMap();

    public ResourceSet() {
        // nothing done here
    }

    ResourceSet(Map<String, Resource> items) {
        mItems.putAll(items);
    }

    public void addSource(File file) throws DuplicateResourceException {
        if (file.isDirectory()) {
            readFolder(file);

        } else if (file.isFile()) {
            // TODO
        }
    }

    public int getSize() {
        return mItems.size();
    }

    public Collection<Resource> getResources() {
        return mItems.values();
    }

    public Map<String, Resource> getResourceMap() {
        return mItems;
    }

    public void writeTo(File rootFolder) throws IOException {
        // map of XML values files to write after parsing all the files.
        // the key is the qualifier.
        Multimap<String, Resource> map = ArrayListMultimap.create();

        for (Resource item : mItems.values()) {
            if (item.getValue() != null) {
                String qualifier = item.getSource().getQualifiers();
                if (qualifier == null) {
                    qualifier = "";
                }

                map.put(qualifier, item);
            } else {
                // we can write the file.
                ResourceFile resourceFile = item.getSource();
                File file = resourceFile.getFile();

                String filename = file.getName();
                String folderName = item.getType().getName();
                String qualifiers = resourceFile.getQualifiers();
                if (qualifiers != null && qualifiers.length() > 0) {
                    folderName = folderName + SdkConstants.RES_QUALIFIER_SEP + qualifiers;
                }

                File typeFolder = new File(rootFolder, folderName);
                if (!typeFolder.isDirectory()) {
                    typeFolder.mkdirs();
                }

                File outFile = new File(typeFolder, filename);
                Files.copy(file, outFile);
            }
        }

        // now write the values files.
        for (String key : map.keySet()) {
            // the key is the qualifier.
            String folderName = key.length() > 0 ?
                    ResourceFolderType.VALUES.getName() + SdkConstants.RES_QUALIFIER_SEP + key :
                    ResourceFolderType.VALUES.getName();

            File valuesFolder = new File(rootFolder, folderName);
            valuesFolder.mkdirs();
            File outFile = new File(valuesFolder, "values.xml");

            // get the list of items to write
            Collection<Resource> items = map.get(key);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setIgnoringComments(true);
            DocumentBuilder builder;

            try {
                builder = factory.newDocumentBuilder();
                Document document = builder.newDocument();

                Node rootNode = document.createElement(SdkConstants.TAG_RESOURCES);
                document.appendChild(rootNode);

                for (Resource item : items) {
                    Node adoptedNode = adoptNode(document, item.getValue());
                    rootNode.appendChild(adoptedNode);
                }

                String content = XmlUtils.toXml(document, false /*preserveWhitespace*/);

                Files.write(content, outFile, Charsets.UTF_8);
            } catch (ParserConfigurationException e) {
                throw new BuildException(e);
            }
        }
    }

    /**
     * Makes a new document adopt a node from a different document, and correctly reassign namespace
     * and prefix
     * @param document the new document
     * @param node the node to adopt.
     * @return the adopted node.
     */
    private Node adoptNode(Document document, Node node) {
        Node newNode = document.adoptNode(node);

        updateNamespace(newNode, document);

        return newNode;
    }

    /**
     * Updates the namespace of a given node (and its children) to work in a given document
     * @param node the node to update
     * @param document the new document
     */
    private void updateNamespace(Node node, Document document) {

        // first process this node
        processSingleNodeNamespace(node, document);

        // then its attributes
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                processSingleNodeNamespace(attributes.item(i), document);
            }
        }

        // then do it for the children nodes.
        NodeList children = node.getChildNodes();
        if (children != null) {
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child != null) {
                    updateNamespace(child, document);
                }
            }
        }
    }

    /**
     * Update the namespace of a given node to work with a given document.
     * @param node the node to update
     * @param document the new document
     */
    private void processSingleNodeNamespace(Node node, Document document) {
        String ns = node.getNamespaceURI();
        if (ns != null) {
            NamedNodeMap docAttributes = document.getAttributes();

            String prefix = getPrefixForNs(docAttributes, ns);
            if (prefix == null) {
                prefix = getUniqueNsAttribute(docAttributes);
                Attr nsAttr = document.createAttribute(prefix);
                nsAttr.setValue(ns);
                document.getChildNodes().item(0).getAttributes().setNamedItem(nsAttr);
            }

            // set the prefix on the node, by removing the xmlns: start
            prefix = prefix.substring(6);
            node.setPrefix(prefix);
        }
    }

    /**
     * Looks for an existing prefix for a a given namespace.
     * The prefix must start with "xmlns:". The whole prefix is returned.
     * @param attributes the list of attributes to look through
     * @param ns the namespace to find.
     * @return the found prefix or null if none is found.
     */
    private String getPrefixForNs(NamedNodeMap attributes, String ns) {
        if (attributes != null) {
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (ns.equals(attribute.getValue()) && ns.startsWith(SdkConstants.XMLNS_PREFIX)) {
                    return attribute.getName();
                }
            }
        }

        return null;
    }

    private String getUniqueNsAttribute(NamedNodeMap attributes) {
        if (attributes == null) {
            return "xmlns:ns1";
        }

        int i = 2;
        while (true) {
            String name = String.format("xmlns:ns%d", i++);
            if (attributes.getNamedItem(name) == null) {
                return name;
            }
        }
    }

    private void readFolder(File rootFolder) throws DuplicateResourceException {
        File[] folders = rootFolder.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    parseFolder(folder);
                }
            }
        }
    }

    private void parseFolder(File folder) throws DuplicateResourceException {

        // get the type.
        String folderName = folder.getName();
        int pos = folderName.indexOf(ResourceConstants.RES_QUALIFIER_SEP);
        ResourceFolderType folderType;
        String qualifiers = null;
        if (pos != -1) {
            folderType = ResourceFolderType.getTypeByName(folderName.substring(0, pos));
            qualifiers = folderName.substring(pos + 1);
        } else {
            folderType = ResourceFolderType.getTypeByName(folderName);
        }

        boolean singleResourceFile = folderType != ResourceFolderType.VALUES;
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);

        // get the files
        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (singleResourceFile) {
                    // get the resource name based on the filename
                    String name = file.getName();
                    pos = name.indexOf('.');
                    name = name.substring(0, pos);

                    Resource item = new Resource(name, types.get(0), null);
                    ResourceFile resourceFile = new ResourceFile(file, item, qualifiers);

                    checkItem(item);

                    mItems.put(item.getKey(), item);
                } else {
                    ValueResourceParser parser = new ValueResourceParser(file);
                    try {
                        List<Resource> items = parser.parseFile();

                        ResourceFile resourceFile = new ResourceFile(file, items, qualifiers);

                        for (Resource item : items) {
                            checkItem(item);
                            mItems.put(item.getKey(), item);
                        }
                    } catch (FileNotFoundException e) {
                        // wont happen as we know the file exists.
                    }
                }
            }
        }
    }

    private void checkItem(Resource item) throws DuplicateResourceException {
        Resource otherItem = mItems.get(item.getKey());
        if (otherItem != null) {
            throw new DuplicateResourceException(item, otherItem);
        }
    }
}
