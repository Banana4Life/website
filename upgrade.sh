#!/bin/bash

base="target/universal/stage"

git pull
kill $(cat $base/RUNNING_PID)

activator clean stage

pushd $base
./bin/website > /dev/null &
popd
