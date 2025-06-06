# Gic Node in Docker

## About Gic
Gic is a decentralized platform that allows any user to issue, transfer, swap and trade custom blockchain tokens on an integrated peer-to-peer exchange. You can find more information about Gic at [gicsports.io](https://gicsports.io/) and in the official [documentation](https://docs.gicsports.io).


## About the image
This Docker image is focused on fast and convenient deployment of Gic Node.
The image contains scripts and configs to run Gic Node for `mainnet`, `testnet` or `stagenet` networks.
If you need to run node in private network, see [Gic private node](https://github.com/gicsportsofficial/GicSmartChain/tree/master/docker#gic-private-node) section.

GitHub repository: https://github.com/gicsportsofficial/GicSmartChain/tree/master/docker

## Prerequisites
It is highly recommended to read more about [Gic Node configuration](https://docs.gicsports.io/en/gic-node/node-configuration) before running the container.

## Building Docker image
`./build-with-docker.sh && docker build -t gicsportsofficial/gic docker` (from the repository root) - builds an image with the current local repository

**You can specify following arguments when building the image:**


| Argument          | Default value | Description                                                                                                                                                                                                                                                                                   |
|-------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `INCLUDE_GRPC`    | `true`        | Whether to include gRPC server files in the image.                                                                                                                                                                                                                                            |

**Note: All build arguments are optional.**

## Running Docker image

### Configuration options

1. The image supports Gic Node config customization. To change a config field use corresponding JVM options. JVM options can be sent to JVM using `JAVA_OPTS` environment variable. Please refer to ([complete configuration file](https://github.com/gicsportsofficial/GicSmartChain/blob/master/node/src/main/resources/application.conf)) to get the full path of the configuration item you want to change.

```
docker run -v /docker/gic/gic-data:/var/lib/gic -v /docker/gic/gic-config:/etc/gic -p 6869:6869 -p 6862:6862 -e JAVA_OPTS="-Dwaves.rest-api.enable=yes -Dwaves.wallet.password=myWalletSuperPassword" -ti gicsportsofficial/gic
```

2. Gic Node is looking for a config in the directory `/etc/gic/gic.conf` which can be mounted using Docker volumes. During image build, a default configuration will be copied to this directory. While running container if the value of `WAVES_NETWORK` is not `mainnet`, `testnet` or `stagenet`, default configuration won't be enough for correct node working. This is a scenario of using `CUSTOM` network - correct configuration must be provided when running container. If you use `CUSTOM` network and `/etc/gic/gic.conf` is NOT found Gic Node container will exit.

3. By default, `/etc/gic/gic.conf` config includes `/etc/gic/local.conf`. Custom `/etc/gic/local.conf` can be used to override default config entries. Custom `/etc/gic/gic.conf` can be used to override or the whole configuration. For additional information about Docker volumes mapping please refer to `Managing data` item.

### Environment variables

**You can run container with predefined environment variables:**

| Env variable                      | Description  |
|-----------------------------------|--------------|
| `WAVES_WALLET_SEED`        		| Base58 encoded seed. Overrides `-Dwaves.wallet.seed` JVM config option. |
| `WAVES_WALLET_PASSWORD`           | Password for the wallet file. Overrides `-Dwaves.wallet.password` JVM config option. |
| `WAVES_LOG_LEVEL`                 | Node logging level. Available values: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. More details about logging are available [here](https://docs.gicsports.io/en/gic-node/logging-configuration).|
| `WAVES_HEAP_SIZE`                 | Default Java Heap Size limit in -X Command-line Options notation (`-Xms=[your value]`). More details [here](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionX.html). |
|`WAVES_NETWORK`                    | Gic Blockchain network. Available values are `mainnet`, `testnet`, `stagenet`.|
|`JAVA_OPTS`                        | Additional Gic Node JVM configuration options. 	|

**Note: All variables are optional.**  

**Note: Environment variables override values in the configuration file.** 

### Managing data
We recommend to store the blockchain state as well as Gic configuration on the host side. As such, consider using Docker volumes mapping to map host directories inside the container:

**Example:**

1. Create a directory to store Gic data:

```
mkdir -p /docker/gic
mkdir /docker/gic/gic-data
mkdir /docker/gic/gic-config
```

Once container is launched it will create:

- three subdirectories in `/docker/gic/gic-data`:
```
/docker/gic/gic-data/log    - Gic Node logs
/docker/gic/gic-data/data   - Gic Blockchain state
/docker/gic/gic-data/wallet - Gic Wallet data
```
- `/docker/gic/gic-config/gic.conf` - default Gic config


3. If you already have Gic Node configuration/data - place it in the corresponsing directories

4. Add the appropriate arguments to ```docker run``` command: 
```
docker run -v /docker/gic/gic-data:/var/lib/gic -v /docker/gic/gic-config:/etc/gic -e WAVES_NETWORK=stagenet -e WAVES_WALLET_PASSWORD=myWalletSuperPassword -ti gicsportsofficial/gic
```

### Blockchain state

If you are a Gic Blockchain newbie and launching Gic Node for the first time be aware that after launch it will start downloading the whole blockchain state from the other nodes. During this download it will be verifying all blocks one after another. This procesure can take some time.

You can speed this process up by downloading a compressed blockchain state from our official resources, extract it and mount inside the container (as discussed in the previous section). In this scenario Gic Node skips block verifying. This is a reason why it takes less time. This is also a reason why you must download blockchain state *only from our official resources*.

**Note**: We do not guarantee the state consistency if it's downloaded from third-parties.

|Network     |Link          |
|------------|--------------|
|`mainnet`   | http://blockchain.gscscan.com/blockchain_last.tar |
|`testnet`   | http://blockchain-testnet.gscscan.com/blockchain_last.tar  |
|`stagenet`  | http://blockchain-stagenet.gscscan.com/blockchain_last.tar |


**Example:**
```
mkdir -p /docker/gic/gic-data

wget -qO- http://blockchain-stagenet.gscscan.com/blockchain_last.tar --show-progress | tar -xvf - -C /docker/gic/gic-data

docker run -v /docker/gic/gic-data:/var/lib/gic gicsportsofficial/Node -e WAVES_NETWORK=stagenet -e WAVES_WALLET_PASSWORD=myWalletSuperPassword -ti gicsportsofficial/gic
```

### Network Ports

1. REST-API interaction with Node. Details are available [here](https://docs.gicsports.io/en/gic-node/node-configuration#rest-api-settings).

2. Gic Node communication port for incoming connections. Details are available [here](https://docs.gicsports.io/en/gic-node/node-configuration#network-settings).


**Example:**
Below command will launch a container:
- with REST-API port enabled and configured on the socket `0.0.0.0:6870`
- Gic node communication port enabled and configured on the socket `0.0.0.0:6868`
- Ports `6868` and `6870` mapped from the host to the container

```
docker run -v /docker/gic/gic-data:/var/lib/gic -v /docker/gic/gic-config:/etc/gic -p 6870:6870 -p 6868:6868 -e JAVA_OPTS="-Dwaves.network.declared-address=0.0.0.0:6868 -Dwaves.rest-api.port=6870 -Dwaves.rest-api.bind-address=0.0.0.0 -Dwaves.rest-api.enable=yes" -e WAVES_WALLET_PASSWORD=myWalletSuperPassword -e WAVES_NETWORK=stagenet -ti gicsportsofficial/gic
```

Check that REST API is up by navigating to the following URL from the host side:
http://localhost:6870/api-docs/index.html

### Extensions
You can run custom extensions in this way:
1. Copy all lib/*.jar files from extension to any directory, lets say `plugins`
2. Add extension class to configuration file, lets say `local.conf`, located in `config` directory containing also `gic.conf`:
```hocon
waves.extensions += com.johndoe.WavesExtension
```
3. Run `docker run -v "$(pwd)/plugins:/usr/share/gic/lib/plugins" -v "$(pwd)/config:/etc/gic" -i gicsportsofficial/gic`

## Gic private node

The image is useful for developing dApps and other smart contracts on Gic blockchain.

### Getting started

To run the node,\
`docker run -d --name gic-private-node -p 6869:6869 gicsportsofficial/gic-private-node`

To view node API documentation, open http://localhost:6869/

### Preserve blockchain state

If you want to keep the blockchain state, then just stop the container instead of killing it, and start it again when needed:\
`docker stop gic-private-node`
`docker start gic-private-node`

### Configuration details

The node is configured with:

- faster generation of blocks (**10 sec** interval)
- all features pre-activated
- custom chain id - **R**
- api_key `gic-private-node`
- default miner account with all Gic tokens (you can distribute these tokens to other accounts as you wish):
  ```
  rich account:
      Seed text:           gic private node seed with gic tokens
      Seed:                TBXHUUcVx2n3Rgszpu5MCybRaR86JGmqCWp7XKh7czU57ox5dgjdX4K4
      Account seed:        HewBh5uTNEGLVpmDPkJoHEi5vbZ6uk7fjKdP5ghiXKBs
      Private account key: 83M4HnCQxrDMzUQqwmxfTVJPTE9WdE7zjAooZZm2jCyV
      Public account key:  AXbaBkJNocyrVpwqTzD4TpUY8fQ6eeRto9k1m2bNCzXV
      Account address:     3M4qwDomRabJKLZxuXhwfqLApQkU592nWxF
  ```

Full node configuration is available on Github in `gic.custom.conf`: https://github.com/gicsportsofficial/GicSmartChain/blob/master/docker/private/gic.custom.conf

### Image tags

You can use the following tags:

- `latest` - currrent version of Mainnet
- `vX.X.X` - specific version of Gic Node
