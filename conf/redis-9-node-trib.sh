#!/usr/bin/env bash

HOST=192.168.255.1
PORTS="7201 7202 7203 7204 7205 7206 7207 7208 7209"
REPLICAS=2

NODES=`for v in ${PORTS}; do echo "$HOST:$v"; done`

echo "$NODES"

./redis-trib.rb create --replicas ${REPLICAS} ${NODES}