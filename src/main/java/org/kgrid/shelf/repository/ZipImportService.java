package org.kgrid.shelf.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.kgrid.shelf.ShelfException;
import org.kgrid.shelf.domain.ArkId;
import org.kgrid.shelf.domain.KnowledgeObject;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.zeroturnaround.zip.ZipUtil;

@Service
public class ZipImportService extends ZipService {

  private final org.slf4j.Logger log = LoggerFactory.getLogger(ZipImportService.class);

  /**
   * Create KO object, must add Knowledge Object files, Knowledge Object properties and Knowledge
   * Object Implementation properties
   *
   * @param arkId ark id of the importing object
   * @param zipFileStream zip in the form of a stream
   * @param cdoStore persistence layer
   */
  public void importObject(ArkId arkId, InputStream zipFileStream,
      CompoundDigitalObjectStore cdoStore) {

    cdoStore.delete(arkId.getDashArk());

    Map<String, JsonNode> containerResources = new HashMap<>();
    Map<String, byte[]> binaryResources = new HashMap<>();

    log.info("loading zip file for " + arkId.getDashArk());
    String trxId = cdoStore.createTransaction();
    try {
      captureZipEntries(zipFileStream, containerResources, binaryResources);

      cdoStore.createContainer(trxId, arkId.getDashArk());

      JsonNode koMetaData = containerResources.get(
          arkId.getDashArk());

      if (KnowledgeObject.getImplementationIDs(koMetaData).isArray()) {

        JsonNode implementationNodes = KnowledgeObject.getImplementationIDs(koMetaData);
        implementationNodes.forEach(jsonNode -> {

          importImplementation(arkId, trxId, cdoStore, containerResources, binaryResources, jsonNode);

        });

      } else {

        importImplementation(arkId, trxId, cdoStore, containerResources, binaryResources,
            KnowledgeObject.getImplementationIDs(koMetaData));

      }

      cdoStore.saveMetadata(koMetaData, trxId, arkId.getDashArk(),
          KnowledgeObject.METADATA_FILENAME);

      cdoStore.commitTransaction(trxId);
    } catch(Exception e) {
      cdoStore.rollbackTransaction(trxId);
      log.warn(e.getMessage());
      throw e;
    }
  }

  /**
   * Captures the Zip Entries loading a collection of metadata and collection of binaries
   *
   * @param zipFileStream zip file in a stream
   * @param containerResources collection of metadata files
   * @param binaryResources collection of binary files
   */
  private void captureZipEntries(InputStream zipFileStream,
      Map<String, JsonNode> containerResources, Map<String, byte[]> binaryResources) {

    log.info("processing zipEntries");
    Map<String, JsonNode> metadataQueue = Collections.synchronizedMap(new LinkedHashMap<>());
    Map<String, byte[]> binaryQueue = Collections.synchronizedMap(new LinkedHashMap<>());

    ZipUtil.iterate(zipFileStream, (inputStream, zipEntry) -> {

      if (!zipEntry.getName().contains("__MACOSX")) {

        if (zipEntry.getName().endsWith(KnowledgeObject.METADATA_FILENAME)) {

          StringWriter writer = new StringWriter();
          IOUtils.copy(inputStream, writer, StandardCharsets.UTF_8);

          JsonNode metadata = new ObjectMapper().readTree(writer.toString());

          try {
            validateMetadata(zipEntry.getName(), metadata);
          } catch (ShelfException e) {
            log.warn(e.getMessage());
            return;
          }

          metadataQueue.put(zipEntry.getName(), metadata);

        } else if (!zipEntry.isDirectory() &&
            !zipEntry.getName().endsWith(KnowledgeObject.METADATA_FILENAME)) {

          binaryQueue.put(zipEntry.getName(), IOUtils.toByteArray(inputStream));
        }
      }
    });

    metadataQueue.forEach((filename, metadata) ->
        containerResources.put(FilenameUtils.normalize(
            filename.substring(0, filename.indexOf(KnowledgeObject.METADATA_FILENAME) - 1)), metadata));

    binaryQueue.forEach((filename, bytes) ->
        binaryResources.put(FilenameUtils.normalize(filename), bytes));
  }

  // Metadata must have an @type field that contains either koio:KnowledgeObject or koio:Implementation,
  // an @id field and an @context field, ignores top-level @graph structures
  void validateMetadata(String filename, JsonNode metadata) {
    String typeLabel = "@type", idLabel = "@id", contextLabel = "@context";
    String ko = "koio:KnowledgeObject", impl = "koio:Implementation";

    if (metadata.has("@graph")) {
      metadata = metadata.get("@graph").get(0);
    }
    if (!metadata.has(idLabel)) {
      throw new ShelfException("Cannot import ko: Missing id label in file " + filename);
    }

    if (metadata.has(typeLabel)) {
      if (metadata.get(typeLabel).isArray()) {
        boolean valid = false;
        Iterator<JsonNode> iter = metadata.get(typeLabel).elements();
        while (iter.hasNext()) {
          JsonNode typeNode = iter.next();
          if (ko.equals(typeNode.asText()) || impl.equals(typeNode.asText())) {
            valid = true;
          }
        }
        if (!valid) {
          throw new ShelfException(
              "Cannot import ko: Missing knowledge object or implementation type in file "
                  + filename);
        }
      } else if (!ko.equals(metadata.get(typeLabel).asText()) &&
          !impl.equals(metadata.get(typeLabel).asText())) {
        throw new ShelfException(
            "Cannot import ko: Missing knowledge object or implementation type in file "
                + filename);
      }
    } else {
      throw new ShelfException("Cannot import ko: Missing type field in file " + filename);
    }
    if (!metadata.has(contextLabel)) {
      throw new ShelfException("Cannot import ko: Missing context in file " + filename);
    }
  }

  /**
   * Imports the KO Implementations loading the metadata and binaries
   *
   * @param arkId Ark Id of the object
   * @param cdoStore persistence layer
   * @param containerResources metadata load from the zip
   * @param binaryResources binaries load based on the metadata in the zip
   * @param jsonNode implementation node
   */
  private void importImplementation(ArkId arkId, String trxId, CompoundDigitalObjectStore cdoStore,
      Map<String, JsonNode> containerResources, Map<String, byte[]> binaryResources,
      JsonNode jsonNode) {

    try {

      //handle absolute and relative IRI (http://localhost:8080/fcrepo/rest/hello-world/v1 vs hello-world/v1)
      String path = ResourceUtils.isUrl(jsonNode.asText()) ?
          ResourceUtils.toURI(jsonNode.asText()).getPath().substring(
              ResourceUtils.toURI(jsonNode.asText()).getPath().indexOf(arkId.getDashArk())) :
          jsonNode.asText();

      JsonNode metadata = containerResources.get(Paths.get(path).toString());

      cdoStore.createContainer(trxId, path);

      List<String> binaryPaths = listBinaryNodes(metadata);

      binaryPaths.forEach((binaryPath) -> {
        try {
          //handle absolute and relative IRI
          // (http://localhost:8080/fcrepo/rest/hello-world/koio.v1/deployment-specification.yaml vs
          // koio.v1/deployment-specification.yaml)
          String filePath = ResourceUtils.isUrl(binaryPath) ?
              ResourceUtils.toURI(binaryPath).getPath().substring(
                  ResourceUtils.toURI(binaryPath).getPath().indexOf(arkId.getDashArk())) :
              Paths.get(arkId.getDashArk(), binaryPath).toString();

          byte[] binaryBytes = binaryResources.get(filePath);

          Objects.requireNonNull(binaryBytes,
              "Issue importing implementation binary can not find " + filePath);

          cdoStore.saveBinary(binaryBytes, trxId, filePath);

        } catch (URISyntaxException e) {
          throw new ShelfException("Issue importing implementation binary ", e);
        }

      });

      cdoStore.saveMetadata(metadata, trxId, path, KnowledgeObject.METADATA_FILENAME);

    } catch (URISyntaxException e) {
      throw new ShelfException("Issue importing implementation ", e);
    }

  }
}
