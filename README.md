# Wink Link

WinkLink is SCAM AND DEAD PROJECT.
an Oracle CopyCat Chainlink and simplifies the communication with blockchains. This initial implementation is intended for use and review by developers,
and will go on to form the basis for WinkLink's decentralized oracle network. Further development of the WinkLink Node and WinkLink Network will happen here,
if you are interested in contributing please contact us, email: service@winklink.org

## Build

1. Install MySQL (v5.7)

2. Install Java

3. Install NodeJS (>=10.0)

4. Build
```
./gradlew clean build
```

## Run node

After build successfully, `node-{version}.jar` will be generated in dir `node/build/libs`.

1. Start MySQL first. Initializing the tables using the latest sql file in path `node/src/main/resources/db/migration`.

2. Start the WinkLink node:

```
java -jar node-v1.0.jar --key key.store 2>&1 &
// customize some configuration
java -jar node-v1.0.jar --server.port=8081 --spring.profiles.active=dev --key key.store 2>&1 &
```

Note:
- The `key.store` file contain the private key that this node use. The format refer to: `node/src/main/resouces/key.store.template`.
- The `vrfKeyStore.yml` file contain the private keys for VRF that this node use. The format refer to: `node/src/main/resouces/vrfKeyStore-template.yml`.
- You can just put a new `application.yml` or `application-{ENV}.yml` in the working dir to replace the default spring config file.
- There is a set of demo contracts deployed on `nile` network, the node will listen on `nile` when starting node with the command: `--env dev`

## Project Structrue

WinkLink is is a monorepo containing several logicaly separatable and relatable projects.

- `tvm-contracts` - smart contracts
- `v1.0/TronOracle.sol` and `v1.0/TronUser.sol` are oracle contracts
- `v1.0/VRF` are VRF contracts
- `node` - the core WinkLink node
- `@node/webapp` - the webapp for WinkLink node

