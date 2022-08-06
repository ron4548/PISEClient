# PISE Client

PISE (Protocol Inference with Symbolic Execution) is a tool that leverages symbolic execution and automata learning to uncover the state machine of a protocol implemented in a given executable. It is available in two modules:

- [The server](https://github.com/ron4548/PISEServer): for performing the symbolic execution. Implemented in Python.
- The client (this repo): responsible for automata learning. Implemented in Java.

#### Prerequisites

- Java JDK:
  - `sudo apt install openjdk-11-jre-headless` 

- [Maven]():
  - `sudo apt install maven`

- [GraphViz](https://graphviz.org/): for displaying state machines
  - `sudo apt install graphviz`


#### Dependencies

* [LearnLib](https://learnlib.de/)

#### Installation

In order to start working with PISE, first clone this repo:

```shell
git clone https://github.com/ron4548/PISEClient.git
cd PISEClient
```

Install jdk, maven and GraphViz:

```shell
sudo apt install openjdk-11-jre-headless maven graphviz
```

Install maven dependencies:

```shell
mvn dependency:resolve
```

Compile PISEClient:

```shell
mvn compile
```

## Running the client

First, you need to run an instance of [The server](https://github.com/ron4548/PISEServer) suitable for the binary you want to reverse engineer.

Second, start the client by running:

```shell
mvn exec:java -Dexec.mainClass="com.pise.client.PiseLearner"
```

## Talks & Paper

The PISE paper is available [here](https://github.com/ron4548/InferenceServer/blob/master/paper.pdf).

Our Black Hat USA 2022 briefing is available [here](https://www.blackhat.com/us-22/briefings/schedule/#automatic-protocol-reverse-engineering-27238).