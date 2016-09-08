# Ethereum Harmony

Ethereum network private peer. Based on EthereumJ implementation. 
 

[![Ethereum Harmony Demo](http://i.imgur.com/zeJMQ94.png)](https://www.youtube.com/watch?v=3qASGOy3qrw )

 
### Features: 

 * Ethereum peer JSON-RPC 2.0 standard ;
 
 * Keeping private keys in filesystem **keystore** compatible with go-ethereum;
 
 * In Browser command line terminal
 
 * Blockchain canonical tracing   
 
 * Monitoring peers connectivity;
 
 * Easy go Ethereum **wallet** 
 


## Prerequisites Installed: To run application locally, system should have already installed: Java 8, Bower
* Java 8
* Node JS
* Bower

## Run 

* `git clone https://github.com/ether-camp/ethereum-harmony`
* `cd ethereum-harmony`
* only on updates: `bower install`
* Run Service: `gradlew bootRun`  ( or any [other option](#options) )

Navigate to `http://localhost:8080`
JSON-RPC is available at `http://localhost:8080/rpc`

(*) Use `gradlew bootRun -Dserver.port=9999` option to change web interface port number.

## Command line options <a id="options"></a>

There are ways to connect to other networks:

`gradlew runMorden` will start server and tell ethereum node to connect to Morden network

`gradlew runTest` will start server and tell ethereum node to connect to Test network

`gradlew runClassic` will start server and tell ethereum node to connect to classic fork

`gradlew runPrivate` will start server without connecting to network. Mining will be enabled after start.

## Importing project to IntelliJ IDEA: 

Idea should have next plugins to be installed:
 
 - Gradle Plugin;
 
 - Lombok Plugin. Annotation processing should be turned on in IDEA compiler. Otherwise compiler will raise compile errors `java: cannot find symbol variable log`;

Checkout and build:

```
> git clone https://github.com/ether-camp/ethereum-harmony.git
> cd ethereum-harmony
> gradlew build
```


## Implementation details

Application is built with Spring Boot and Angular 1. Websocket messaging and REST methods are used for client-server communication.
Lombok library is used to avoid writing generic code over data objects.
 
Original JSON-RPC specification located here: https://github.com/ethereum/wiki/wiki/JSON-RPC .   
Harmony contains improved copy of *jsonrpc* package from *ethereumj-core*.   


### Wallet

Wallet allows to use addresses by keeping private keys on server and without.

1. App is able to store and use private key in password protected format in user file system. 
Location of directory is universal among other ethereum nodes and located:
 *   Mac: `~/Library/Ethereum`
 *   Linux: `~/.ethereum`
 *   Windows: `%APPDATA%\Ethereum`

2. User is able to add watch-only addresses without sharing private key with server.
App will prompt user to enter private key for such addresses. Otherwise user also have possibility to enter:
    - seed word, sha3 of which will produce private key;
    - or mnemonic phrases, which also will produce private key after applying sha3 2031 times.

[License GPL 3.0](https://github.com/ether-camp/ethereum-harmony/blob/master/LICENSE)
