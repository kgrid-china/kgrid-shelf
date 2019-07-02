package org.kgrid.shelf.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.kgrid.shelf.ShelfException;
import org.kgrid.shelf.domain.ArkId;
import org.kgrid.shelf.repository.KnowledgeObjectRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Api(tags = {"Knowledge Object API"} )
public class KnowledgeObjects extends KOSController {

  public KnowledgeObjects(KnowledgeObjectRepository shelf,
      Optional<KnowledgeObjectDecorator> kod) {
    super(shelf, kod);
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Finds all knowledge objects",
      notes = "Returns a collection of knowledge objects in the from of a JSON array. The JSON objects"
              + " will follow the <a href=\"http://kgrid.org/koio/contexts/knowledgeobject.jsonld\">KOIO "
          + "scheme</a> for knowledge objects"
  )
  public Map getAllObjects() {
    log.info("getting all kos");
    Map koMap = shelf.findAll();
    log.info("found " + koMap.size() + " kos");
    return koMap;
  }
  @GetMapping(path = "/{naan}/{name}",  headers = "Accept=application/zip", produces = "application/zip")
  public void exportKnowledgeObject( @PathVariable String naan, @PathVariable String name,
      HttpServletResponse response) {

    log.info("get ko zip for " + naan + "/" + name);
    ArkId arkId = new ArkId(naan, name);

    exportZip(response, arkId);
  }

  @GetMapping(path = "/{naan}/{name}",  produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Find a Knowledge Object based on naan and name and returns JSON or Zip",
      notes = "Finds and returns a knowledge object based on naan and name. Can response with a JSON object defined but "
          + "<a href=\"http://kgrid.org/koio/contexts/knowledgeobject.jsonld\">KOIO scheme</a>  or can package the knowledge object into a zip file",
      produces = "application/json, application/zip")
    public ResponseEntity<JsonNode> findKnowledgeObject(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name) {

    log.info("get ko " + naan + "/" + name);

    // Prevent infinite loop when trying to connect to fcrepo on the same address as the library
    if ("fcrepo".equals(naan) && "rest".equals(name)) {
      throw new IllegalArgumentException(
          "Cannot connect to fcrepo at the same address as the shelf. Make sure shelf and fcrepo configuration is correct.");
    }
    ArkId arkId = new ArkId(naan, name);
    JsonNode results = shelf.findKnowledgeObjectMetadata(arkId);

    if (results == null || results.size() == 0) {
      throw new IllegalArgumentException("Object not found with id " + naan + "-" + name);
    }

    return new ResponseEntity<>(results, HttpStatus.OK);
  }


  @GetMapping(path = "/{naan}/{name}/{implementation}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Find a Knowledge Object Implementation based on naan, name and implementation and returns JSON or Zip",
      notes = "Finds and returns a knowledge object Implementation based on naan and name and implementation. Can response with a JSON object defined but "
          + "<a href=\"http://kgrid.org/koio/contexts/implementation.jsonld\">KOIO scheme</a> or can package the knowledge object implementation into a zip file",
      produces = "application/json, application/zip")
  public JsonNode getKnowledgeObjectImplementation(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name,
      @ApiParam(value="Ark Implementation", example="v0.1.0")  @PathVariable String implementation) {

    log.info("getting ko " + naan + "/" + name + "/" + implementation);

    ArkId arkId = new ArkId(naan, name, implementation);

    return shelf.findImplementationMetadata(arkId);
  }

  @GetMapping(path = "/{naan}/{name}/{implementation}", headers = "Accept=application/zip", produces = "application/zip")
  public void exportKnowledgeObjectImplementation(@PathVariable String naan, @PathVariable String name,
      @PathVariable String implementation, HttpServletResponse response) {

    log.info("get ko zip for " + naan + "/" + name);
    ArkId arkId = new ArkId(naan, name, implementation);

    exportZip(response, arkId);
  }


  @GetMapping(path = "/{naan}/{name}/{implementation}/service", produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Find a Knowledge Object Implementation's Open API Service specification",
      notes = "")
  public Object getServiceDescription(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name,
      @ApiParam(value="Ark Implementation", example="v0.1.0")  @PathVariable String implementation) {

    log.info("getting ko service  " + naan + "/" + name + "/" + implementation);

    ArkId arkId = new ArkId(naan, name, implementation);

    return shelf.findServiceSpecification(arkId);

  }

  @GetMapping(path = "/{naan}/{name}/{implementation}/**", produces = MediaType.ALL_VALUE)
  @ApiOperation(value = "Find a Knowledge Object Implementation based on on naan, name,  implementation and path to resource",
      notes = "")
  public Object getBinary(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name,
      @ApiParam(value="Ark Implementation", example="v0.1.0")  @PathVariable String implementation, HttpServletRequest request) throws NoSuchFileException {

    log.info("getting ko resource " + naan + "/" + name + "/" + implementation);

    ArkId arkId = new ArkId(naan, name, implementation);

    String requestURI = request.getRequestURI();
    String basePath = StringUtils.join(naan, "/", name, "/", implementation, "/");
    String childPath = StringUtils.substringAfterLast(requestURI, basePath);

    log.info("getting ko resource " + naan + "/" + name + "/" + implementation + childPath);

    byte[] binary = shelf.getBinaryOrMetadata(arkId, childPath);
    if (binary != null) {
      return binary;
    } else {
      throw new NoSuchFileException("Cannot fetch file at " + childPath);
    }
  }


  @PutMapping(path = "/{naan}/{name}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Update Knowledge Object ",
      notes = "")
  public ResponseEntity<JsonNode> editKnowledgeObjectOMetadata(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name,
      @RequestBody String data) {
    ArkId arkId = new ArkId(naan, name);
    shelf.editMetadata(arkId, null, data);
    return new ResponseEntity<>(shelf.findKnowledgeObjectMetadata(arkId),
        HttpStatus.OK);
  }

  @PutMapping(path = "/{naan}/{name}/{implementation}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Update Knowledge Object Implementation",
      notes = "")
  public ResponseEntity<JsonNode> editImplmentaionMetadata(@PathVariable String naan,
      @PathVariable String name,
      @PathVariable String implementation,
      @RequestBody String data) {
    ArkId arkId = new ArkId(naan, name, implementation);
    shelf.editMetadata(arkId, null, data);
    return new ResponseEntity<>(shelf.findImplementationMetadata(arkId),
        HttpStatus.OK);
  }

  @DeleteMapping(path = "/{naan}/{name}")
  @ApiOperation(value = "Delete Knowledge Object ",
      notes = "")
  public ResponseEntity<String> deleteKnowledgeObject(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name) {
    ArkId arkId = new ArkId(naan, name);
    shelf.delete(arkId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);

  }

  @DeleteMapping(path = "/{naan}/{name}/{implementation}")
  @ApiOperation(value = "Delete Knowledge Object Implementation",
      notes = "")
  public ResponseEntity<String> deleteKnowledgeObject(
      @ApiParam(value="Name Assigning Authority unique number", example="hello") @PathVariable String naan,
      @ApiParam(value="Ark Name", example="world") @PathVariable String name,
      @ApiParam(value="Ark Implementation", example="v0.1.0")  @PathVariable String implementation) {
    ArkId arkId = new ArkId(naan, name, implementation);
    shelf.deleteImpl(arkId);
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);

  }

  @ApiOperation(value = "Imports a packaged Knowledge Object as a zip file",
      notes = "Takes a packaged Knowledge Object as a zip file in a ko form field")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, String>> depositKnowledgeObject(
     @RequestParam("ko") MultipartFile zippedKo) {

    log.info("Add ko via zip");
    ArkId arkId = shelf.importZip(zippedKo);

    Map<String, String> response = new HashMap<>();
    HttpHeaders headers = addKOHeaderLocation(arkId);
    response.put("Added", arkId.getDashArk());

    return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
  }

  @PostMapping( path = "/manifest", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(value = "Imports a packaged Knowledge Object as a list URLs to "
      + "packaged Knowledge Objects",
      notes = "JSON Manifest example"
          + "{\n"
          + "    \"manifest\": [\n"
          + "        \"https://github.com/kgrid-objects/example-collection/releases/download/1.4.0/hello-world.zip\",\n"
          + "        \"https://github.com/kgrid-objects/example-collection/releases/download/1.4.0/score-calc.zip\",\n"
          + "    ]\n"
          + "}",
      consumes = "application/json", nickname = "Import")
  public ResponseEntity<Map<String, Object>> depositKnowledgeObject (
      @ApiParam(value="JSON Manifest with URLs to packaged Knowledge Objects")

      @RequestBody JsonNode requestBody) {

    log.info("Add kos from manifest {}", requestBody.asText());

    if(!requestBody.has("manifest")) {
      throw new IllegalArgumentException("Provide manifest field with url or array of urls as the value");
    }

    Map<String, Object> response = new HashMap<>();
    try {
      if(requestBody.get("manifest").isArray()) {
        ArrayNode arkList = new ObjectMapper().createArrayNode();
        log.info( "importing {} kos", requestBody.get("manifest").size());
        requestBody.get("manifest").forEach(ko -> {
          String koLocation = ko.asText();
          try {
            URL koURL = new URL(koLocation);
            log.info( "import {}", koLocation);
            arkList.add((shelf.importZip(koURL.openStream())).toString());
          }  catch (Exception ex) {
            log.warn( "Error importing {}, {}",koLocation, ex.getMessage());
          }
        });
        response.put("Added", arkList);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
      } else {
        String koLocation = requestBody.get("ko").asText();
        URL koURL = new URL(koLocation);
        ArkId arkId = shelf.importZip(koURL.openStream());
        response.put("Added", arkId);

        HttpHeaders headers = addKOHeaderLocation(arkId);

        return new ResponseEntity<>(response, headers, HttpStatus.CREATED);
      }

    } catch (IOException ex) {
      throw new ShelfException(ex);
    }

  }

  protected void exportZip(HttpServletResponse response, ArkId arkId) {

    response.setHeader("Content-Type","application/octet-stream");
    response.addHeader("Content-Disposition",
        "attachment; filename=\"" + (arkId.isImplementation() ?
            arkId.getDashArk() + "-" + arkId.getImplementation() : arkId.getDashArk()) + ".zip\"");
    try {
      shelf.extractZip(arkId, response.getOutputStream());
    } catch (IOException ex) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    } finally {
      try {
        response.getOutputStream().close();
      } catch (IOException e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    }
  }


  private HttpHeaders addKOHeaderLocation(ArkId arkId) {

    URI loc = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .pathSegment(arkId.getSlashArk())
        .build().toUri();
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(loc);
    return headers;
  }

}
