package edu.umich.lhs.activator.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.umich.lhs.activator.domain.ArkId;
import edu.umich.lhs.activator.domain.CompoundKnowledgeObject;
import edu.umich.lhs.activator.domain.KnowledgeObject;
import edu.umich.lhs.activator.domain.Payload;
import edu.umich.lhs.activator.domain.SimpleKnowledgeObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeObjectRepository {

  private CompoundDigitalObjectStoreFactory factory;
  private final org.slf4j.Logger log = LoggerFactory.getLogger(KnowledgeObjectRepository.class);

  @Autowired
  KnowledgeObjectRepository(CompoundDigitalObjectStoreFactory factory) {
    this.factory = factory;
  }

  public KnowledgeObject getCompoundKnowledgeObject(ArkId arkId, String version) {
    CompoundDigitalObjectStore dataStore = factory.create(arkId.getFedoraPath());

    KnowledgeObject ko = new CompoundKnowledgeObject(arkId, version);
    ObjectNode metadataNode = dataStore.getMetadata(ko.baseMetadataLocation());
    JsonNode modelMetadataNode = dataStore.getMetadata(ko.modelMetadataLocation());
    metadataNode.set("models", modelMetadataNode);
    ko.setMetadata(metadataNode);
    return ko;
  }

  public SimpleKnowledgeObject convertCompoundToSimpleKObject(ArkId arkId, String version) {
    CompoundDigitalObjectStore dataStore = factory.create(arkId.getFedoraPath());
    SimpleKnowledgeObject sko = new SimpleKnowledgeObject();
    KnowledgeObject ko = getCompoundKnowledgeObject(arkId, version);

    byte[] inputMessage = dataStore.getBinary(ko.serviceLocation().resolve("input.xml"));
    sko.setInputMessage(new String(inputMessage, Charset.defaultCharset()));
    byte[] outputMessage = dataStore.getBinary(ko.serviceLocation().resolve("output.xml"));
    sko.setOutputMessage(new String (outputMessage, Charset.defaultCharset()));
    sko.setMetadata(ko.getMetadata());

    Payload payload = new Payload();
    payload.setContent(new String(dataStore.getBinary(ko.resourceLocation()), Charset.defaultCharset()));
    payload.setEngineType(ko.adapterType());
    payload.setFunctionName(ko.getMetadata().get("models").get("functionName").asText());

    sko.setPayload(payload);
    return sko;
  }

  public SimpleKnowledgeObject getSimpleKnowledgeObject(ArkId arkId) throws URISyntaxException {
    CompoundDigitalObjectStore dataStore = factory.create();
    URI koPath = new URI(arkId.getFedoraPath());
    ObjectNode koJson;
    if(Files.exists(Paths.get(koPath))) {
      koJson = dataStore.getMetadata(koPath);
    } else {
      koPath = new URI(arkId.getFedoraPath() + ".json");
      koJson = dataStore.getMetadata(koPath);
    }
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.treeToValue(koJson, SimpleKnowledgeObject.class);
    } catch (JsonProcessingException jpEx) {
      throw new IllegalArgumentException("Cannot convert file " + arkId + " to simple ko");
    }
  }

  public Map<String, ObjectNode> knowledgeObjectVersions(ArkId arkId) {
    CompoundDigitalObjectStore dataStore = factory.create(arkId.getFedoraPath());
    Map<String, ObjectNode> versionMap = new HashMap<>();
    try {
      List<String> versions = dataStore.getChildren(new URI(arkId.getFedoraPath()));
      for (String version : versions) {
        versionMap.put(version, getCompoundKnowledgeObject(arkId, version).getMetadata());
      }
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return versionMap;
  }

  public List<ObjectNode> getAllObjects(){
    CompoundDigitalObjectStore dataStore = factory.create();
    List<ObjectNode> knowledgeObjects = new ArrayList<>();

    List<ArkId> arkIds = dataStore.getChildren(null).stream()
        .map(name -> {
            try {return new ArkId(name);
          } catch (IllegalArgumentException | NullPointerException e) {
            log.error(e.getMessage());return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    for (ArkId arkId : arkIds) {
      knowledgeObjects.addAll(knowledgeObjectVersions(arkId).values());
    }
    return knowledgeObjects;
  }

  public ArkId saveKnowledgeObject(MultipartFile zippedKO) {
    CompoundDigitalObjectStore dataStore = factory.create();
    ObjectNode jsonData = dataStore.addCompoundObjectToShelf(zippedKO);
    return new ArkId(jsonData.get("metadata").get("arkId").get("arkId").asText());
  }

  public void removeKO(ArkId arkId) throws IOException, URISyntaxException {
    CompoundDigitalObjectStore dataStore = factory.create();
    dataStore.removeFile(new URI(arkId.getFedoraPath()));

  }

}
