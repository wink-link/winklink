# Wink Link Web Console
Wink Link Web Console is a tool that allows developers to manage the current active jobs across 
different nodes. Other than being able to view the lists of active jobs based on contracts,
you will be able to search for contract that you wanted as well as filter out datasource
that you might want to monitor. 

## Requirements
* node.js (v14.21.2)

## Running
To run with .env file, create a `.env.local` in dir
`node/webapp`. In the `.env.local` using this as an example.

```
API_URL = "http://localhost:8080"
API_URLS = [{"text":"winklink-price-001" ,"value":"http://localhost:8080"},{"text":"winklink-price-002", "value":"http://localhost:8081"}]
DATASOURCE_SIZE_PER_RETRIEVAL = 100
LOCALE= "en-SG"
TIMEZONE= "Asia/Singapore"
```
* **API_URL** is the url that would fall back to.
* **API_URLS** is the list of nodes that you are running,
even if you are running a single node, you are still required to put in the below example.
```
[{"text":"node1" ,"value":"http://localhost:8080"}]
```
* **DATASOURCE_SIZE_PER_RETRIEVAL** is the size of records per retrieval.
* **LOCALE** is the format of date you would like to see in the table.
* **TIMEZONE** is the timezone of date you would like to see in the table.

If you would not like to create an `.env` file, you can append the details in 
`env.js` which is in dir `node/webapp/config/env.js`.

## Run Locally
```bash
> npm install
to start with localhost, use
> npm start 
to start with ip, use
> HOST=x.x.x.x npm start
```


## To Build and Serve

```bash
> npm run build
```
