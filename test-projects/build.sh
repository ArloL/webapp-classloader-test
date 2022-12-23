#!/bin/sh

set -o errexit
set -o nounset
set -o xtrace

set -- \
    webapp-test-keystore

cd "$(dirname "$0")" || exit 1

for project in "$@"; do
	cd "./${project}" || exit 1
	mvn clean package
	mv "target/${project}.war" "../../src/test/resources/${project}.war"
	cd ".." || exit 1
done
