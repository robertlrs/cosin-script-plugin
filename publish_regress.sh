#!/bin/bash

mvn clean package


scp -P10022 /Users/luorenshu/workspace/open_source/cosin-script-plugin/target/releases/cosin-script-plugin-6.2.4.zip devops@172.16.4.25:/home/devops/robert/
