#!/bin/bash

base="target/universal/stage"

git pull
kill $(cat $base/RUNNING_PID)
activator clean stage

ln -s {..,$base}/twitter4j.properties
ln -s {..,$base}/secret.conf

pushd $base
./bin/website > /dev/null &
popd
