# Ethereum Harmony

Application for demonstrating EthereumJ features. It runs local Java based node https://github.com/ethereum/ethereumj . Next features were implemented:

 * JSON-RPC implementation and usage statistics. Note, that some JSON-RPC methods are not implemented yet;
 
 * Keeping private keys in filesystem keystore compatible with go-ethereum;
 
 * Browser based terminal for calling JSON-RPC methods;
 
 * Display blockchain info, like last block details, blocks hierarchy;  
 
 * Display physical machine info;
  
 * Connected peers displayed in list view and visualized on world map;
 
 * Monitoring system logs in web browser;
 
 * Ether wallet with abilities to view balance, and send amount with private key, keystore, mnemonic phrases.

Application is not designed for public access as may expose private info without restriction.

### Setup

System should have already installed: Java 8, Bower

First `bower install`

Then to run server: `gradlew bootRun`

Navigate to `http://localhost:8080`

### Command line options

There are ways to connect to other networks:

`gradlew runMorden` will start server and tell ethereum node to connect to Morden network

`gradlew runTest` will start server and tell ethereum node to connect to Test network

`gradlew runClassic` will start server and tell ethereum node to connect to classic fork

`gradlew runPrivate` will start server without connecting to network. Mining will be enabled after start.

### Implementation details

Application is built with Spring Boot and Angular 1. Websocket messaging and REST methods are used for client-server communication.
Lombok library is used to avoid writing generic code over data objects. 


##### Importing project to IntelliJ IDEA: 

Idea should have next plugins to be installed:
 
 - Gradle Plugin;
 
 - Lombok Plugin. Annotation processing should be turned on in IDEA compiler. Otherwise compiler will raise compile errors `java: cannot find symbol variable log`;

Checkout and build:

```
> git clone https://github.com/ether-camp/ethereum-harmony.git
> cd ethereum-harmony
> gradlew build
```

IDEA: 

* File -> New -> Project from existing sources…
* Select ethereum-harmony/build.gradle
* Dialog “Import Project from gradle”: press “OK”
* After building run either Gradle launcher with task bootRun 



