# Letter of Credit Revamp



# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Usage

## Running tests inside IntelliJ


## Running the nodes

See https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp.







## Usage

There's essentially five processes you'll need to be aware of.

- Three Corda nodes, a notary, santa, and an elf
- The backend webserver that runs the REST endpoints for the corda nodes
- The frontend webserver, a react app that sends requests to the backend.


#### Pre-Requisites

If you've never built a cordapp before you may need to configure gradle and java in order for this code example to run. See [our setup guide](https://docs.corda.net/getting-set-up.html).


### Running these services

#### The three Corda nodes
To run the corda nodes you just need to run the `deployNodes` gradle task and the nodes will be available for you to run directly.

```
./gradlew deployNodes
./build/nodes/runnodes
```

#### The backend webserver

Run the `runSantaServer` Gradle task. By default, it connects to the node with RPC address `localhost:10006` with
the username `user1` and the password `test`, and serves the webserver on port `localhost:10056`.

```
./gradlew runSantaServer
```

The frontend will be visible on [localhost:10056](http://localhost:10056)

##### Background Information

`clients/src/main/java/com/secretsanta/webserver/` defines a simple Spring webserver that connects to a node via RPC and allows you to interact with the node over HTTP.

The API endpoints are defined in `clients/src/main/java/com/secretsanta/webserver/Controller.java`


#### The frontend webserver

The react server can be started by going to `clients/src/main/webapp`, running `npm install` and then `npm start`.


```
cd clients/src/main/webapp
npm install
npm run serve
```

The frontend will be visible on [localhost:3000](http://localhost:3000)



#### Running tests inside IntelliJ

There are unit tests for the corda state, contract, and tests for both flows used here. You'll find them inside of the various test folders.













