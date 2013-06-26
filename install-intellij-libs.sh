#!/bin/bash
# This script will install all files in IntelliJ IDEA's lib/ folder to the local maven .m2 repository. This way we can use them during the build
#
# Usage:
#   ./install-intellij-libs.sh 12.0.4 /my/intellij/installation/folder
#
# Source: https://github.com/gshakhn/sonar-intellij-plugin/blob/master/install-intellij-libs.sh

IDEA_VERSION=$1
INTELLIJ_HOME=$2

if [ -z "$INTELLIJ_HOME" ]
then
  echo "Please provide the version and path to the IntelliJ home directory. For example: ./install-intellij-libs.sh 12.0.4 /Users/ahe/Applications/IntelliJ-IDEA-12.app/"
  exit 1
fi

if [ ! -d "$INTELLIJ_HOME" ]
then
  echo "Directory does not exist: $INTELLIJ_HOME"
  exit 1
fi

echo 'Installing Intellij artifacts to Maven local repository'
echo "Intellij home: $INTELLIJ_HOME"
for i in `ls -1 ${INTELLIJ_HOME}/lib/*.jar`
do
    FOLDERS=(${i//\// })
    FILE_POS=${#FOLDERS[@]}
    JAR_FILE=${FOLDERS[${FILE_POS}-1]%.jar}
    mvn install:install-file -Dfile="$INTELLIJ_HOME/lib/${JAR_FILE}.jar" -DgroupId=com.intellij -DartifactId=${JAR_FILE} -Dversion=${IDEA_VERSION} -Dpackaging=jar
done

mvn install:install-file -Dfile="$INTELLIJ_HOME/plugins/git4idea/lib/git4idea.jar" -DgroupId=com.intellij -DartifactId=git4idea -Dversion=${IDEA_VERSION} -Dpackaging=jar
