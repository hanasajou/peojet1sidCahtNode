#!/bin/bash
if [ $# -lt 2 ]; then
  echo "Usage: ./run_node.sh <port> <username>"
  exit 1
fi
javac ChatNode.java || exit 1
java ChatNode "$1" "$2"