#!/usr/bin/env bash

while :; do redis-cli -h 192.168.255.1 -p $1 cluster info | grep cluster_state; sleep 1; done
