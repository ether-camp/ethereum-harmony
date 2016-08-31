# Ethereum Harmony

### Setup

System should have already installed: Java 8, Bower

First `bower install`

Then to run server: `gradlew bootRun`

Navigate to `http://localhost:8080`

### Command line options

There are ways to connect to other networks:

`gradlew runMorden` will start server and tell ethereum node to connect to Morden network

`gradlew runTest` will start server and tell ethereum node to connect to Test network

`gradlew runPrivate` will start server without connecting to network. Mining will be enabled after start.


