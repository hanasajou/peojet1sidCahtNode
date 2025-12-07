# peojet1sidCahtNode
P2P Chat - Simple Java Peer-to-Peer Chat Node
=============================================

Files:
- ChatNode.java      : main program (contains ChatNode and PeerConnection classes)
- run_node.sh        : convenience script to run a node (Linux/macOS)
- run_node.bat       : convenience script to run a node (Windows)
- README.md          : this file

How it works:
- Each node listens on a TCP port for incoming peer connections.
- You can connect to other peers by using:  connect <host> <port>
- To broadcast a message to all connected peers:  msg <your text>
- Commands: connect, msg, peers, history, help, quit

Compile:
$ javac ChatNode.java

Run (example):
# Terminal A - start node listening on port 7000 as 'alice'
$ java ChatNode 7000 alice

# Terminal B - start node listening on port 7001 as 'bob'
$ java ChatNode 7001 bob

# In bob's terminal, connect to alice:
> connect 127.0.0.1 7000
# Then send messages:
> msg Hello Alice!

The message will appear in the other node's terminal.

Notes:
- This is a simple educational implementation. It uses plain-text messages and broadcasts to all connected peers.
- For a classroom project you can extend it: add private messages, peer discovery, history persistence, GUI, encryption, etc.

