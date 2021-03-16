#!/usr/bin/env bash

# This script (postconfigure.sh) is executed during launch of the application server (not during the build)
# This script is expected to be copied to $JBOSS_HOME/extensions/ folder by script install.sh

STANDALONE_XML='standalone-openshift.xml'

# on JBoss EAP with standalone-openshift.xml, on with WildFly standalone.xml
if [ ! -f "${JBOSS_HOME}/standalone/configuration/${STANDALONE_XML}" ]; then
  STANDALONE_XML='standalone.xml'
  sed -i "s/serverConfig=.*/serverConfig=${STANDALONE_XML}/" "${POSTCONFIGURE_PROPERTIES_FILE}"
fi

# Container does not provide PostgreSQL driver
# It's most probably JBoss EAP. WildFly OpenShift image should contain the driver.
if [ ! -f "${JBOSS_HOME}/modules/org/postgresql/jdbc/main/module.xml" ]; then
  echo "Creating PostgreSQL JDBC module and driver under ${STANDALONE_XML}"
  "${JBOSS_HOME}"/bin/jboss-cli.sh "embed-server --server-config=${STANDALONE_XML},\
    module add --name=org.postgresql.jdbc --module-xml=${JBOSS_HOME}/extensions/postgresql-module.xml"
  "${JBOSS_HOME}"/bin/jboss-cli.sh "embed-server --server-config=${STANDALONE_XML},\
    /subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql.jdbc,driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)"
fi
