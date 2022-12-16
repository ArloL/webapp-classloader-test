#!/bin/sh

set -o errexit
set -o nounset
set -o xtrace

set -- \
    webapp-test-keystore

for project in "$@"; do
	cd "$(dirname "$0")/${project}" || exit 1
	mvn clean package
	mv "target/${project}.war" "../../src/test/resources/${project}.war"
done
