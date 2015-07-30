#!/usr/bin/env bash

HOST=192.168.255.1
PORTS="7001 7002 7003 7004 7005 7006"
REPLICAS=1

NODES=`for v in ${PORTS}; do echo "$HOST:$v"; done`

echo "$NODES"

./redis-trib.rb create --replicas ${REPLICAS} ${NODES}