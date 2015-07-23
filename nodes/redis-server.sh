#!/usr/bin/env bash

INSTANCES=3
START_PORT=7001

CONF_DIR=/etc/redis
RUN_DIR=/var/run
DB_DIR=/var/lib
LOG_DIR=/var/log/redis

INIT_SCRIPT=/etc/init.d/redis-server

 Redis Installation

add-apt-repository -y ppa:chris-lea/redis-server
apt-get -y update
# apt-get -y upgrade
apt-get -y install redis-server redis-tools ruby

gem install redis

curl http://download.redis.io/redis-stable/src/redis-trib.rb > /usr/bin/redis-trib.rb
chmod 755 /usr/bin/redis-trib.rb

/bin/cat <<EOM >/etc/security/limits.d/redis.conf
redis        soft    nofile  1024
redis        hard    nofile  102400
EOM

# Nodes configuration

function generate_node_files() {

local PORT="$1"

local NODE_CONF=${CONF_DIR}/redis-${NODE_PORT}.conf
local NODE_PID=${RUN_DIR}/redis-server-${NODE_PORT}.pid
local NODE_DB_DIR=${DB_DIR}/redis-${NODE_PORT}
local NODE_LOG=${LOG_DIR}/redis-server-${NODE_PORT}.log

mkdir -p ${NODE_DB_DIR}
chown redis:redis "${NODE_DB_DIR}"

/bin/cat <<EOM >${NODE_CONF}
daemonize yes
cluster-enabled yes

bind 0.0.0.0
port ${PORT}

pidfile ${NODE_PID}
dir ${NODE_DB_DIR}

loglevel notice
logfile ${NODE_LOG}
EOM


local NODE_CONF_ESCAPED=${NODE_CONF//\//\\/}
local NODE_PID_ESCAPED=${NODE_PID//\//\\/}

local NODE_INIT_SCRIPT=${INIT_SCRIPT}-${NODE_PORT}

sed -e "s/DAEMON_ARGS=\/etc\/redis\/redis.conf/DAEMON_ARGS=${NODE_CONF_ESCAPED}/g;s/PIDFILE=\$RUNDIR\/redis-server.pid/PIDFILE=${NODE_PID_ESCAPED}/g" "${INIT_SCRIPT}" > ${NODE_INIT_SCRIPT}

chmod 755 ${NODE_INIT_SCRIPT}
}

for i in `seq 1 ${INSTANCES}`;
do
    NODE_PORT="$((START_PORT + i - 1))"

    generate_node_files ${NODE_PORT}
done

