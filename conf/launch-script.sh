#!/usr/bin/env sh

addJava() {
    return 0
}

is_cygwin() {
    return 1 # we don't care fore cygwin
}

app_home="$(dirname "$(dirname "$(realpath "$0")")")"
lib_dir="$(realpath "${app_home}/lib")"
app_mainclass="${{app_mainclass}}"

${{template_declares}}

exec java \
    -Dplay.server.pidfile.path=/dev/null \
    -Duser.dir="$(dirname "$app_home")" \
    $JAVA_ARGS \
    -cp "$app_classpath" \
    "$app_mainclass" \
    "$@"
