#!/bin/sh

git pull
kill $(cat target/universal/stage/RUNNING_PID)
activator clean stage
pushd target/universal/stage
./bin/website > /dev/null &
popd
