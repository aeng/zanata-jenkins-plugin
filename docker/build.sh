#!/usr/bin/env bash

# determine directory containing this script
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

PLUGIN=$(ls $DIR/../target/*.hpi)

if [ -f "$PLUGIN" ]
then
    ln ${PLUGIN} ${DIR}/zanata-repo-sync.hpi
else
    echo please build the plugin first
    exit 1
fi

docker build -t zjenkins/dev ${DIR}

rm ${DIR}/zanata-repo-sync.hpi

