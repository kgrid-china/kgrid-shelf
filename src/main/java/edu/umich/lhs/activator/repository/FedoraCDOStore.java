package edu.umich.lhs.activator.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


public class FedoraCDOStore implements CompoundDigitalObjectStore {

  private String userName;

  private String password;

  private URI storagePath;

  public FedoraCDOStore(String userName, String password, URI storagePath) {
    this.userName = userName;
    this.password = password;
    this.storagePath = storagePath;

  }

  @Override
  public List<String> getChildren(URI filePath) {
    Model rdf = null;
    try {
       rdf = getRdfJson(new URI(storagePath.toString() + filePath.toString()));
    } catch (URISyntaxException ex) {
      ex.printStackTrace();
    }
    List<String> children = new ArrayList<>();
    StmtIterator iterator = rdf.listStatements();
    while(iterator.hasNext()) {

      Statement statement = iterator.nextStatement();
      if(statement.getPredicate().getLocalName().equals("contains")) {
        children.add(statement.getObject().toString().substring(storagePath.toString().length()));
      }
    }
    return children;
  }

  @Override
  public URI getAbsoluteLocation(URI filePath) {
    try {
      if(filePath != null) {
        return new URI(storagePath.toString() + "/" + filePath.toString());
      } else {
        return storagePath;
      }
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public ObjectNode getMetadata(URI filePath) {
    try {
      Model metadataRDF = getRdfJson(new URI(storagePath + "/" + filePath));
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }
    return null;
  }

  @Override
  public byte[] getBinary(URI filePath) {
    return new byte[0];
  }

  @Override
  public void saveMetadata(URI destination, JsonNode metadata) {

  }

  @Override
  public void saveBinary(URI destination, byte[] data) {

  }

  @Override
  public ObjectNode addCompoundObjectToShelf(MultipartFile zip) {
    HttpClient instance = HttpClientBuilder.create()
        .setRedirectStrategy(new DefaultRedirectStrategy()).build();
    RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(instance));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(new MediaType("application", "n-triples"));
    headers.putAll(authenticationHeader().getHeaders());
    HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

//

    try {
      ZipInputStream zis = new ZipInputStream(zip.getInputStream());
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {

        if (!entry.getName().contains("/.")) {
          URI dir = new URI(storagePath.toString() + "/" + entry.getName());
          if (!entry.isDirectory()) {
            System.out.println(
                "Zip entry filename: " + entry.getName() + " filesize: " + entry.getSize() + " dir: " + dir);
            if(entry.getName().endsWith("metadata.json")) {
              dir = new URI(dir.toString().substring(0, dir.toString().length() - ("metadata.json".length() + 1)));
              StringBuilder jsonString = new StringBuilder();
              Scanner sc = new Scanner(zis);
              while (sc.hasNextLine()) {
                jsonString.append(sc.nextLine());
              }
              JsonNode node = (new ObjectMapper().readTree(jsonString.toString()));
              Model metadataModel = ModelFactory.createDefaultModel();
              Resource resource = metadataModel.createResource(dir.toString());

              node.fields().forEachRemaining(element -> resource.addLiteral(
                  metadataModel.createProperty(element.getKey()), element.getValue()));
              StringWriter writer = new StringWriter();
              metadataModel.write(writer, "N-TRIPLE");
              System.out.println(writer.toString());
              RequestEntity request = RequestEntity.put(new URI(dir.toString() + "/fcr:metadata"))
                  .header("Authorization", authenticationHeader().getHeaders().getFirst("Authorization"))
                  .header("Prefer", "handling=lenient; received=\"minimal\"")
                  .contentType(new MediaType("application", "n-triples", StandardCharsets.UTF_8))
                  .body(writer.toString());
              ResponseEntity<String> response = restTemplate.exchange(request, String.class);
              System.out.println("Got response for " + dir + " " + response);
            }
          }
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }

  @Override
  public void removeFile(URI filePath) throws IOException {
    
  }

  public Model getRdfJson(URI objectURI) {

    HttpClient instance = HttpClientBuilder.create()
        .setRedirectStrategy(new DefaultRedirectStrategy()).build();

    RestTemplate restTemplate = new RestTemplate(
        new HttpComponentsClientHttpRequestFactory(instance));
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(new MediaType("application", "ld+json")));
    headers.putAll(authenticationHeader().getHeaders());

    HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

    ResponseEntity<String> response = restTemplate.exchange(objectURI, HttpMethod.GET,
        entity, String.class);

    InputStream ins = new ByteArrayInputStream(response.getBody().getBytes());

    Model model = ModelFactory.createDefaultModel().read(ins, this.storagePath.toString(), "JSON-LD");

    try {
      ins.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
    return model;

  }

  public URI createContainer(URI uri) throws URISyntaxException {
    HttpClient instance = HttpClientBuilder.create()
        .setRedirectStrategy(new DefaultRedirectStrategy()).build();

    RestTemplate restTemplate = new RestTemplate(
        new HttpComponentsClientHttpRequestFactory(instance));

    ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.PUT,
        authenticationHeader(), String.class);

    return new URI(response.getHeaders().getFirst("Location"));
  }

  private HttpEntity<HttpHeaders> authenticationHeader() {
    final String CHARSET = "US-ASCII";
    HttpHeaders header = new HttpHeaders();
    String auth = userName + ":" + password;
    byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName(CHARSET)));
    String authHeader = "Basic " + new String(encodedAuth);
    header.set("Authorization", authHeader);
    return new HttpEntity<>(header);
  }

}
