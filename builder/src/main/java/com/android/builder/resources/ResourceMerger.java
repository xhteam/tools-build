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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.AaptRunner;
import com.android.builder.internal.util.concurrent.WaitableExecutor;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Merges {@link ResourceSet}s and writes a resource folder that can be fed to aapt.
 *
 * This is able to save its post work state and reload this for incremental update.
 */
public class ResourceMerger implements ResourceMap {

    static final String FN_MERGER_XML = "merger.xml";
    private static final String FN_VALUES_XML = "values.xml";
    private static final String NODE_MERGER = "merger";
    private static final String NODE_RESOURCE_SET = "resourceSet";

    /**
     * All the resources. The merged version will be the last item in the list.
     */
    private final List<ResourceSet> mResourceSets = Lists.newArrayList();

    public ResourceMerger() { }

    /**
     * adds a new {@link ResourceSet} and overlays it on top of the existing ResourceSets.
     *
     * @param resourceSet the ResourceSet to add.
     */
    public void addResourceSet(ResourceSet resourceSet) {
        // TODO figure out if we allow partial overlay through a per-resource flag.
        mResourceSets.add(resourceSet);
    }

    /**
     * Returns the list of ResourceSet objects.
     * @return the resource sets.
     */
    @VisibleForTesting
    List<ResourceSet> getResourceSets() {
        return mResourceSets;
    }

    @VisibleForTesting
    void validateResourceSets() throws DuplicateResourceException {
        for (ResourceSet resourceSet : mResourceSets) {
            resourceSet.checkItems();
        }
    }

    /**
     * Returns the number of resources.
     * @return the number of resources.
     *
     * @see ResourceMap
     */
    @Override
    public int size() {
        // put all the resource keys in a set.
        Set<String> keys = Sets.newHashSet();

        for (ResourceSet resourceSet : mResourceSets) {
            ListMultimap<String, Resource> map = resourceSet.getResourceMap();
            keys.addAll(map.keySet());
        }

        return keys.size();
    }

    /**
     * Returns a map of the resources.
     * @return a map of items.
     *
     * @see ResourceMap
     */
    @NonNull
    @Override
    public ListMultimap<String, Resource> getResourceMap() {
        // put all the sets in a multimap. The result is that for each key,
        // there is a sorted list of items from all the layers, including removed ones.
        ListMultimap<String, Resource> fullItemMultimap = ArrayListMultimap.create();

        for (ResourceSet resourceSet : mResourceSets) {
            ListMultimap<String, Resource> map = resourceSet.getResourceMap();
            for (Map.Entry<String, Collection<Resource>> entry : map.asMap().entrySet()) {
                fullItemMultimap.putAll(entry.getKey(), entry.getValue());
            }
        }

        return fullItemMultimap;
    }

    /**
     * Writes the result of the merge to a destination resource folder.
     *
     * The output is an Android style resource folder than can be fed to aapt.
     *
     * @param rootFolder the folder to write the resources in.
     * @param aaptRunner an aapt runner.
     * @throws IOException
     * @throws DuplicateResourceException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void writeResourceFolder(@NonNull File rootFolder, @Nullable AaptRunner aaptRunner)
            throws IOException, DuplicateResourceException, ExecutionException,
            InterruptedException {
        WaitableExecutor executor = new WaitableExecutor();

        // get all the resource keys.
        Set<String> resourceKeys = Sets.newHashSet();

        for (ResourceSet resourceSet : mResourceSets) {
            // quick check on duplicates in the resource set.
            resourceSet.checkItems();
            ListMultimap<String, Resource> map = resourceSet.getResourceMap();
            resourceKeys.addAll(map.keySet());
        }

        // map of XML values files to write after parsing all the files.
        // the key is the qualifier.
        ListMultimap<String, Resource> valuesResMap = ArrayListMultimap.create();
        // set of qualifier that was a previously written resource disappear. This is to keep track
        // of which file to write if no other resources are touched.
        Set<String> qualifierWithDeletedValues = Sets.newHashSet();

        // loop on all the resources.
        for (String resourceKey : resourceKeys) {
            // for each resource, look in the resource sets, starting from the end of the list.

            Resource previouslyWritten = null;
            Resource toWrite = null;

            /*
             * We are looking for what to write/delete: the last non deleted item, and the
             * previously written one.
             */

            setLoop: for (int i = mResourceSets.size() - 1 ; i >= 0 ; i--) {
                ResourceSet resourceSet = mResourceSets.get(i);

                // look for the resource key in the set
                ListMultimap<String, Resource> resourceMap = resourceSet.getResourceMap();

                List<Resource> resources = resourceMap.get(resourceKey);
                if (resources.isEmpty()) {
                    continue;
                }

                // The list can contain at max 2 items. One touched and one deleted.
                // More than one deleted means there was more than one which isn't possible
                // More than one touched means there is more than one and this isn't possible.
                for (int ii = resources.size() - 1 ; ii >= 0 ; ii--) {
                    Resource resource = resources.get(ii);

                    if (resource.isWritten()) {
                        assert previouslyWritten == null;
                        previouslyWritten = resource;
                    }

                    if (toWrite == null && !resource.isRemoved()) {
                        toWrite = resource;
                    }

                    if (toWrite != null && previouslyWritten != null) {
                        break setLoop;
                    }
                }
            }

            // done searching, we should at least have something.
            assert previouslyWritten != null || toWrite != null;

            // now need to handle, the type of each (single res file, multi res file), whether
            // they are the same object or not, whether the previously written object was deleted.

            if (toWrite == null) {
                // nothing to write? delete only then.
                assert previouslyWritten.isRemoved();

                ResourceFile.FileType type = previouslyWritten.getSource().getType();

                if (type == ResourceFile.FileType.SINGLE) {
                    removeOutFile(rootFolder, previouslyWritten.getSource());
                } else {
                    qualifierWithDeletedValues.add(previouslyWritten.getSource().getQualifiers());
                }

            } else if (previouslyWritten == null || previouslyWritten == toWrite) {
                // easy one: new or updated res

                writeResource(rootFolder, valuesResMap, toWrite, executor, aaptRunner);
            } else {
                // replacement of a resource by another.

                // first force the writing of the new one.
                toWrite.setTouched();

                // write the new value
                writeResource(rootFolder, valuesResMap, toWrite, executor, aaptRunner);

                ResourceFile.FileType previousType = previouslyWritten.getSource().getType();
                ResourceFile.FileType newType = toWrite.getSource().getType();

                if (previousType == newType) {
                    // if the type is multi, then we make sure to flag the
                    // qualifier as deleted.
                    if (previousType == ResourceFile.FileType.MULTI) {
                        qualifierWithDeletedValues.add(
                                previouslyWritten.getSource().getQualifiers());
                    }
                } else if (newType == ResourceFile.FileType.SINGLE) {
                    // new type is single, so old type is multi.
                    // ensure the previous one is deleted by forcing rewrite of its associated
                    // qualifiers.
                    qualifierWithDeletedValues.add(previouslyWritten.getSource().getQualifiers());
                } else {
                    // new type is values, and old is single res file.
                    // delete the old single res file
                    removeOutFile(rootFolder, previouslyWritten.getSource());
                }
            }
        }

        // now write the values files.
        for (String key : valuesResMap.keySet()) {
            // the key is the qualifier.

            // check if we have to write the file due to deleted values.
            // also remove it from that list anyway (to detect empty qualifiers later).
            boolean mustWriteFile = qualifierWithDeletedValues.remove(key);

            // get the list of items to write
            Collection<Resource> items = valuesResMap.get(key);

            // now check if we really have to write it
            if (!mustWriteFile) {
                for (Resource item : items) {
                    if (item.isTouched()) {
                        mustWriteFile = true;
                        break;
                    }
                }
            }

            if (mustWriteFile) {
                String folderName = key.length() > 0 ?
                        ResourceFolderType.VALUES.getName() + SdkConstants.RES_QUALIFIER_SEP + key :
                        ResourceFolderType.VALUES.getName();

                File valuesFolder = new File(rootFolder, folderName);
                createDir(valuesFolder);
                File outFile = new File(valuesFolder, FN_VALUES_XML);

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
                        Node adoptedNode = NodeUtils.adoptNode(document, item.getValue());
                        rootNode.appendChild(adoptedNode);
                    }

                    String content = XmlPrettyPrinter.prettyPrint(document);

                    Files.write(content, outFile, Charsets.UTF_8);
                } catch (ParserConfigurationException e) {
                    throw new IOException(e);
                }
            }
        }

        // now remove empty values files.
        for (String key : qualifierWithDeletedValues) {
            String folderName = key != null && key.length() > 0 ?
                    ResourceFolderType.VALUES.getName() + SdkConstants.RES_QUALIFIER_SEP + key :
                    ResourceFolderType.VALUES.getName();

            removeOutFile(rootFolder, folderName, FN_VALUES_XML);
        }

        executor.waitForTasks();
    }

    /**
     * Removes a file that already exists in the out res folder.
     * @param outFolder the out res folder
     * @param sourceFile the source file that created the file to remove.
     * @return true if success.
     */
    private static boolean removeOutFile(File outFolder, ResourceFile sourceFile) {
        if (sourceFile.getType() == ResourceFile.FileType.MULTI) {
            throw new IllegalArgumentException("SourceFile cannot be a FileType.MULTI");
        }

        File file = sourceFile.getFile();
        String fileName = file.getName();
        String folderName = file.getParentFile().getName();

        return removeOutFile(outFolder, folderName, fileName);
    }

    /**
     * Removes a file from a folder based on a sub folder name and a filename
     *
     * @param outFolder the root folder to remove the file from
     * @param folderName the sub folder name
     * @param fileName the file name.
     * @return true if success.
     */
    private static boolean removeOutFile(File outFolder, String folderName, String fileName) {
        File valuesFolder = new File(outFolder, folderName);
        File outFile = new File(valuesFolder, fileName);
        return outFile.delete();
    }

    /**
     * Writes a given Resource to a given root res folder.
     * If the Resource is to be written in a "Values" folder, then it is added to a map instead.
     *
     * @param rootFolder the root res folder
     * @param valuesResMap a map of existing values-type resources where the key is the qualifiers
     *                     of the values folder.
     * @param resource the resource to add.
     * @param executor an executor
     * @param aaptRunner an aapt runner.
     * @throws IOException
     */
    private void writeResource(@NonNull final File rootFolder,
                               @NonNull ListMultimap<String, Resource> valuesResMap,
                               @NonNull final Resource resource,
                               @NonNull WaitableExecutor executor,
                               @Nullable final AaptRunner aaptRunner) throws IOException {
        ResourceFile.FileType type = resource.getSource().getType();

        if (type == ResourceFile.FileType.MULTI) {
            // this is a resource for the values files

            // just add the node to write to the map based on the qualifier.
            // We'll figure out later if the files needs to be written or (not)

            String qualifier = resource.getSource().getQualifiers();
            if (qualifier == null) {
                qualifier = "";
            }

            valuesResMap.put(qualifier, resource);
        } else {
            // This is a single value file.
            // Only write it if the state is TOUCHED.
            if (resource.isTouched()) {
                executor.execute(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        ResourceFile resourceFile = resource.getSource();
                        File file = resourceFile.getFile();

                        String filename = file.getName();
                        String folderName = resource.getType().getName();
                        String qualifiers = resourceFile.getQualifiers();
                        if (qualifiers != null && qualifiers.length() > 0) {
                            folderName = folderName + SdkConstants.RES_QUALIFIER_SEP + qualifiers;
                        }

                        File typeFolder = new File(rootFolder, folderName);
                        createDir(typeFolder);

                        File outFile = new File(typeFolder, filename);

                        if (aaptRunner != null && filename.endsWith(".9.png")) {
                            // run aapt in single crunch mode on the original file to write the
                            // destination file.
                            aaptRunner.crunchPng(file, outFile);
                        } else {
                            Files.copy(file, outFile);
                        }
                        return null;
                    }
                });
            }
        }
    }

    /**
     * Writes a single blog file to store all that the ResourceMerger knows about.
     *
     * @param blobRootFolder the root folder where blobs are store.
     * @throws java.io.IOException
     *
     * @see #loadFromBlob(java.io.File)
     */
    public void writeBlobTo(File blobRootFolder) throws IOException {
        // write "compact" blob
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        DocumentBuilder builder;

        try {
            builder = factory.newDocumentBuilder();
            Document document = builder.newDocument();

            Node rootNode = document.createElement(NODE_MERGER);
            document.appendChild(rootNode);

            for (ResourceSet resourceSet : mResourceSets) {
                Node resourceSetNode = document.createElement(NODE_RESOURCE_SET);
                rootNode.appendChild(resourceSetNode);

                resourceSet.appendToXml(resourceSetNode, document);
            }

            String content = XmlPrettyPrinter.prettyPrint(document);

            createDir(blobRootFolder);
            Files.write(content, new File(blobRootFolder, FN_MERGER_XML), Charsets.UTF_8);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    /**
     * Loads the merger state from a blob file.
     *
     * @param blobRootFolder the folder containing the blob.
     * @return true if the blob was loaded.
     * @throws IOException
     *
     * @see #writeBlobTo(java.io.File)
     */
    public boolean loadFromBlob(File blobRootFolder) throws IOException {
        File file = new File(blobRootFolder, FN_MERGER_XML);
        if (!file.isFile()) {
            return false;
        }

        BufferedInputStream stream = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            stream = new BufferedInputStream(new FileInputStream(file));
            InputSource is = new InputSource(stream);
            factory.setNamespaceAware(true);
            factory.setValidating(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);

            // get the root node
            Node rootNode = document.getDocumentElement();
            if (rootNode == null || !NODE_MERGER.equals(rootNode.getLocalName())) {
                return false;
            }

            NodeList nodes = rootNode.getChildNodes();

            for (int i = 0, n = nodes.getLength(); i < n; i++) {
                Node node = nodes.item(i);

                if (node.getNodeType() != Node.ELEMENT_NODE ||
                        !NODE_RESOURCE_SET.equals(node.getLocalName())) {
                    continue;
                }

                ResourceSet resourceSet = ResourceSet.createFromXml(node);
                if (resourceSet != null) {
                    mResourceSets.add(resourceSet);
                }
            }

            setResourcesToWritten();

            return true;
        } catch (FileNotFoundException e) {
            throw new IOException(e);
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } catch (SAXException e) {
            throw new IOException(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Sets all existing resources to have their state be WRITTEN.
     *
     * @see com.android.builder.resources.Resource#isWritten()
     */
    private void setResourcesToWritten() {
        ListMultimap<String, Resource> resources = ArrayListMultimap.create();

        for (ResourceSet resourceSet : mResourceSets) {
            ListMultimap<String, Resource> map = resourceSet.getResourceMap();
            for (Map.Entry<String, Collection<Resource>> entry : map.asMap().entrySet()) {
                resources.putAll(entry.getKey(), entry.getValue());
            }
        }

        for (String key : resources.keySet()) {
            List<Resource> resourceList = resources.get(key);
            resourceList.get(resourceList.size() - 1).resetStatusToWritten();
        }
    }

    /**
     * Checks that a loaded merger can be updated with a given list of ResourceSet.
     *
     * For now this means the sets haven't changed.
     *
     * @param resourceSets the resource sets.
     * @return true if the update can be performed. false if a full merge should be done.
     */
    public boolean checkValidUpdate(List<ResourceSet> resourceSets) {
        if (resourceSets.size() != mResourceSets.size()) {
            return false;
        }

        for (int i = 0, n = resourceSets.size(); i < n; i++) {
            ResourceSet localSet = mResourceSets.get(i);
            ResourceSet newSet = resourceSets.get(i);

            List<File> localSourceFiles = localSet.getSourceFiles();
            List<File> newSourceFiles = newSet.getSourceFiles();

            // compare the config name and source files sizes.
            if (!newSet.getConfigName().equals(localSet.getConfigName()) ||
                    localSourceFiles.size() != newSourceFiles.size()) {
                return false;
            }

            // compare the source files. The order is not important so it should be normalized
            // before it's compared.
            // make copies to sort.
            localSourceFiles = Lists.newArrayList(localSourceFiles);
            Collections.sort(localSourceFiles);
            newSourceFiles = Lists.newArrayList(newSourceFiles);
            Collections.sort(newSourceFiles);

            if (!localSourceFiles.equals(newSourceFiles)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a ResourceSet that contains a given file.
     *
     * "contains" means that the ResourceSet has a source file/folder that is the root folder
     * of this file. The folder and/or file doesn't have to exist.
     *
     * @param file the file to check
     * @return a pair containing the ResourceSet and its source file that contains the file.
     */
    public Pair<ResourceSet, File> getResourceSetContaining(File file) {
        for (ResourceSet resourceSet : mResourceSets) {
            File sourceFile = resourceSet.findMatchingSourceFile(file);
            if (file != null) {
                return Pair.of(resourceSet, sourceFile);
            }
        }

        return null;
    }

    private synchronized void createDir(File folder) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Failed to create directory: " + folder);
        }
    }


    @Override
    public String toString() {
        return Arrays.toString(mResourceSets.toArray());
    }
}
