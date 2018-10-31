# KGrid Shelf
[![CircleCI](https://circleci.com/gh/kgrid/kgrid-shelf/tree/master.svg?style=shield)](https://circleci.com/gh/kgrid/kgrid-shelf/tree/master)
[![GitHub release](https://img.shields.io/github/release/kgrid/kgrid-shelf.svg)](https://github.com/kgrid/kgrid-shelf/releases/)

KGrid Shelf - (access, acquisition) view Knowledge Objects  (and sets of Knowledge Objects ) & their components; deposit & remove published versions of Knowledge Objects; copy versions of Knowledge Objects
s between Libraries & Activators (Knowledge Objects are immutable).

KGrid Shelf has two modules, the shelf api and gateway component.  The api is used but other KGrid 
components to provide access to the KOs while the gateway wraps the api in a covenant spring boot for easy RESTFul access to the shelf api.  

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites
For building and running the application you need:

- [JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Maven 3](https://maven.apache.org)
- [Docker](https://www.docker.com/) for Integration Tests 

### Clone
To get started you can simply clone this repository using git:
```
git clone https://github.com/kgrid/kgrid-shelf.git
cd kgrid-shelf
```

### Quick start
This quick start will run the activator and load two example knowledge objects for testing.  
This example can loads a sample KO shelf (_where to look for the KOs_) via the _kgrid.shelf.cdostore.filesystem.location_ property. By default application will start up and PORT 8080. 
```
mvn clean package
java -jar target/kgrid-shelf*.jar --kgrid.shelf.cdostore.url=filesystem:file://shelf
```
Once Running access the [Activators Health Endpoint](http://localhost:8080/health).  All _statuses_ reported should be **UP**

```$xslt
{
  "status": "UP",
  "shelf": {
    "status": "UP",
    "kgrid.shelf.cdostore.url": "filesystem:file://shelf/"
  },
  "diskSpace": {
    "status": "UP",
    "total": 499963170816,
    "free": 421147205632,
    "threshold": 10485760
  }
}
   
```

## Unit tests

Unit tests can be executed via mvn test

```
mvn clean test
```


## Integration tests

For integration tests we need to have a Fedora Commons instance running.  We use 
[Fedora image](https://hub.docker.com/r/kgrid/fcrepo/) based on [Fedora Docker](https://hub.docker.com/r/yinlinchen/fcrepo4-docker/) 
which is part of [Fedora Labs](https://github.com/fcrepo4-labs).  This coupled with
[docker-maven-plugin](https://github.com/fabric8io/docker-maven-plugin) maintained by
 [fabric8io](https://fabric8.io/) allows us to spin up fcrepo and run shelf tests against a running
 fcrepo instance.  The container is stopped and removed after the verify. There is a fcrepo_it 
 maven profile that will configure the instance and run the tests
 
```
mvn clean verify -P fcrepo_it
```
  
The first time we run _mvn verify -P fcrepo_it_ the docker image with only the fcreop running.  
The build will wait until the fcrepo is up and running than will run the integration tests.  
 
 Integration tests are identified using _@Category(FedoraIntegrationTest.class)_. Once the tests are complete the docker container will be stopped and removed.

**Tips and Tricks**

 * You can start up fcrepo docker instance with 
 
```
mvn docker:start -P fcrepo_it
``` 

and stop it with 

```
mvn docker:stop -P fcrepo_it
```

Yo can keep the fcrepo container running after the tests running with the _docker.keepRunning_ switch
```
 mvn -Ddocker.keepRunning clean verify -P fcrepo_it

```
Once started, access [Docker FCRepo](http://localhost:8080/fcrepo/rest/)

## End to End Testing

Sample shelf in place the following tests can be executed against the running activator


## Fedora Repository Characterization Tests
A set of tests that help the KGrid team understand the behavior of the [Fedora Repository](https://wiki.duraspace.org/display/FF) in 
terms of [JSON LD](https://json-ld.org/) and our [KOIO](http://kgrid.org/koio) ontology.  Details can
be found at Fedora Repository Characterization Tests [readme](etc/fcrepo/readme.md)


View All Knowlege Objects on the shelf

```
curl http://localhost:8080
```

View a Knowledge Object

```
curl http://localhost:8080/hello/world
```

View a Knowledge Object Version

```
curl http://localhost:8080/hello/world/v0.0.1
```

## Additional Information

The port of the shelf gateway can be altered via the _server.port_ property 
```
java -jar shelf-gateway/target/shelf-gateway-*-boot.jar --kgrid.shelf.cdostore.url=filesystem:file://etc/shelf --server.port=8090

```