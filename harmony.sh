#!/bin/bash

# Startup script to initialize and boot a go-ethereum instance.
#
# This script assumes the following files:
#  - `geth` binary is located in the filesystem root
#  - `genesis.json` file is located in the filesystem root (mandatory)
#  - `chain.rlp` file is located in the filesystem root (optional)
#  - `blocks` folder is located in the filesystem root (optional)
#  - `keys` folder is located in the filesystem root (optional)
#
# This script assumes the following environment variables:
#  - HIVE_BOOTNODE       enode URL of the remote bootstrap node
#  - HIVE_TESTNET        whether testnet nonces (2^20) are needed
#  - HIVE_NODETYPE       sync and pruning selector (archive, full, light)
#  - HIVE_FORK_HOMESTEAD block number of the DAO hard-fork transition
#  - HIVE_FORK_DAO_BLOCK block number of the DAO hard-fork transition
#  - HIVE_FORK_DAO_VOTE  whether the node support (or opposes) the DAO fork
#  - HIVE_MINER          address to credit with mining rewards (single thread)
#  - HIVE_MINER_EXTRA    extra-data field to set for newly minted blocks

# Immediately abort the script on any error encountered
set -e

# It doesn't make sense to dial out, use only a pre-set bootnode
if [ "$HIVE_BOOTNODE" != "" ]; then
	FLAGS="$FLAGS --bootnodes $HIVE_BOOTNODE"
	echo "Missing --bootnodes impl"
else
	FLAGS="$FLAGS -Ddiscovery.enabled=false"
fi

# If the client is to be run in testnet mode, flag it as such
# TODO
if [ "$HIVE_TESTNET" == "1" ]; then
#	FLAGS="$FLAGS --testnet"
echo "Missing --testnet impl"
fi

# Handle any client mode or operation requests
# TODO
if [ "$HIVE_NODETYPE" == "full" ]; then
#	FLAGS="$FLAGS --fast"
echo "Missing --fast impl"
fi
# TODO
if [ "$HIVE_NODETYPE" == "light" ]; then
#	FLAGS="$FLAGS --light"
    echo "Missing --light impl"
fi

# Override any chain configs in the Harmony specific way
chainconfig="{}"
if [ "$HIVE_FORK_HOMESTEAD" != "" ]; then
	chainconfig=`echo $chainconfig | jq ". + {\"homesteadBlock\": $HIVE_FORK_HOMESTEAD}"`
fi
if [ "$HIVE_FORK_DAO_BLOCK" != "" ]; then
	chainconfig=`echo $chainconfig | jq ". + {\"daoForkBlock\": $HIVE_FORK_DAO_BLOCK}"`
fi
if [ "$HIVE_FORK_DAO_VOTE" == "0" ]; then
	chainconfig=`echo $chainconfig | jq ". + {\"daoForkSupport\": false}"`
fi
if [ "$HIVE_FORK_DAO_VOTE" == "1" ]; then
	chainconfig=`echo $chainconfig | jq ". + {\"daoForkSupport\": true}"`
fi
if [ "$chainconfig" != "{}" ]; then
	genesis=`cat /genesis.json` && echo $genesis | jq ". + {\"config\": $chainconfig}" > /genesis.json
fi

# Initialize the local testchain with the genesis state
echo "Initializing database with genesis state..."
#FLAGS="$FLAGS -DgenesisFile=genesis.json"

FLAGS="$FLAGS -Dserver.port=8545"
FLAGS="$FLAGS -Ddatabase.dir=database"
FLAGS="$FLAGS -Dlogs.keepStdOut=true"
FLAGS="$FLAGS -Dpeer.bind.ip=0.0.0.0"
FLAGS="$FLAGS -Dpeer.discovery.external.ip=0.0.0.0"
FLAGS="$FLAGS -Dpeer.discovery.bind.ip=0.0.0.0"

# Temporary
FLAGS="$FLAGS -Dpeer.discovery.enabled=false"

# Load the test chain if present
if [ -f /chain.rlp ]; then
    echo "Loading initial blockchain..."
    FLAGS="$FLAGS -Dblock.loader=/chain.rlp"
fi

# Load the remainder of the test chain
# TODO
if [ -d /blocks ]; then
    echo "Loading remaining individual blocks..."
    echo "Missing --blocks import impl"
#	for block in `ls /blocks | sort -n`; do
#		/geth $FLAGS import /blocks/$block
#	done
fi

# Load any keys explicitly added to the node
# TODO
if [ -d /keys ]; then
#	FLAGS="$FLAGS --keystore /keys"
    echo "Missing --keystore impl"
fi

# Configure any mining operation
# TODO
if [ "$HIVE_MINER" != "" ]; then
#	FLAGS="$FLAGS --mine --minerthreads 1 --etherbase $HIVE_MINER"
    echo "Missing --mine impl"
fi
if [ "$HIVE_MINER_EXTRA" != "" ]; then
	FLAGS="$FLAGS -Dmine.extraData=$HIVE_MINER_EXTRA"
fi

# Run the go-ethereum implementation with the requested flags
echo "Parameters $FLAGS"
echo "Running Harmony..."
cd /ethereum-harmony
./gradlew bootRun $FLAGS
#./gradlew bootRun $FLAGS > out.log &
#sleep 5s
