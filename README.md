

[![Slack Status](http://harmony-slack-ether-camp.herokuapp.com/badge.svg)](http://ether.camp) 


# Ethereum Harmony

Ethereum network private peer. Based on EthereumJ implementation. 


[![Ethereum Harmony Demo](http://i.imgur.com/zeJMQ94.png)](https://www.youtube.com/watch?v=3qASGOy3qrw )

Smart contract trace sample: 

https://www.youtube.com/watch?v=leaAMTgjvxg

 
### Features: 

 * Ethereum peer JSON-RPC 2.0 standard ;
 
 * Keeping private keys in filesystem **keystore** compatible with go-ethereum;
 
 * In Browser command line terminal;
 
 * Blockchain canonical tracing;   
 
 * Monitoring peers connectivity;
 
 * Easy go Ethereum **wallet**;
  
 * Full trace of contract storage locally; 

.

## Prerequisites Installed: 
 * Java 8 (64 bit)

.

## Run 

* `git clone https://github.com/ether-camp/ethereum-harmony`
* `cd ethereum-harmony`
* Run Service: `gradlew runMain`  ( live network by default or any [other environment](#options) )

Navigate to `http://localhost:8080`
JSON-RPC is available at either `http://localhost:8080/rpc` or `http://localhost:8080`

(*) Use `gradlew runMain -Dserver.port=9999` option to change web interface port number.

.

## Command line options <a id="options"></a>

| Environment        | Run      | ... |
| ------------- |:-------------|------------- |
| Main      | `gradlew runMain` | Start server connecting to **Main** network |
| Ropsten      | `gradlew runRopsten` | Start server connecting to **Ropsten** network |
| Test      | `gradlew runTest`      | Start server connecting to **Test** network |
| Classic | `gradlew runClassic`      | Start server connecting to **Ethereum Classic** network |   
| Private | `gradlew runPrivate`      | Start server, no network connection, single miner running|    
| Custom | `gradlew runCustom`      | Start server connecting to custom network (check [custom network](#custom-net)) |

.

## Run in a custom network <a id="custom-net"></a>
* `git clone https://github.com/ether-camp/ethereum-harmony`
* `cd ethereum-harmony`
* Create a custom config file formatted as [ethereumj.conf](https://github.com/ethereum/ethereumj/blob/master/ethereumj-core/src/main/resources/ethereumj.conf) and amend necessary options. File content should look like that:
  ```
  genesisFile = /path/to/custom/genesis.json
  database.dir = database-private

  peer {
    networkId = 10101
    listen.port = 50505

    discovery.enabled = false
    active = [
      {url = "enode://6ce05930c72abc632c58e2e4324f7c7ea478cec0ed4fa2528982cf34483094e9cbc9216e7aa349691242576d552a2a56aaeae426c5303ded677ce455ba1acd9d@13.84.180.240:30303"}
      {url = "enode://20c9ad97c081d63397d7b685a412227a40e23c8bdc6688c6f37e97cfbc22d2b4d1db1510d8f61e6a8866ad7f0e17c02b14182d37ea7c3c8b9c2683aeb6b733a1@52.169.14.227:30303"}
    ]
  }

  sync.fast.enabled = false
  ```
  **Note:** options that haven't been substituted by custom config will be supplied from [ethereumj.conf](https://github.com/ethereum/ethereumj/blob/master/ethereumj-core/src/main/resources/ethereumj.conf)

* Place Genesis file to the path specified by `genesisFile`; good sample is [ropsten.json](https://github.com/ethereum/ethereumj/blob/master/ethereumj-core/src/main/resources/genesis/ropsten.json)

* Run Service: `gradlew runCustom -Dethereumj.conf.file=/path/to/custom.conf`

Navigate to `http://localhost:8080`
JSON-RPC is available at either `http://localhost:8080/rpc` or `http://localhost:8080`

(*) Use `gradlew runCustom -Dethereumj.conf.file=/path/to/custom.conf -Dserver.port=8545` option to change web interface port number.

.

.

.

### Special thanks

Powered by [![multi-platform installer builder](https://www.ej-technologies.com/images/product_banners/install4j_medium.png)](https://www.ej-technologies.com/products/install4j/overview.html)

### License

📜 ... [License GPL 3.0](https://github.com/ether-camp/ethereum-harmony/blob/master/LICENSE)
