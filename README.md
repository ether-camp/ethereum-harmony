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

.

## Prerequisites Installed: 
* Java 8
* Node JS
* Bower

.

## Run 

* `git clone https://github.com/ether-camp/ethereum-harmony`
* `cd ethereum-harmony`
* only on updates: `bower install`
* Run Service: `gradlew bootRun`  ( live by default or any [other environment](#options) )

Navigate to `http://localhost:8080`
JSON-RPC is available at `http://localhost:8080/rpc`

(*) Use `gradlew bootRun -Dserver.port=9999` option to change web interface port number.

.

## Command line options <a id="options"></a>

| Environment        | Run      | ... |
| ------------- |:-------------|------------- |
| Live      | `gradlew bootRun` | Start server connecting to **Live** network |
| Morden      | `gradlew runMorden` | Start server connecting to **Morden** network |
| Test      | `gradlew runTest`      | Start server connecting to **Test** network |
| Classic | `gradlew runClassic`      | Start server connecting to **Ethereum Classic** network |   
| Private | `gradlew runPrivate`      | Start server, no network connection, single miner running|    

.


.


.



### License

📜 ... [License GPL 3.0](https://github.com/ether-camp/ethereum-harmony/blob/master/LICENSE)
