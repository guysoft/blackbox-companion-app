#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/host-tests
javac --release 17 -d build/host-tests app/src/main/java/org/guysoft/blackbox/Protocol.java \
    app/src/main/java/org/guysoft/blackbox/KeyHolds.java \
    app/src/main/java/org/guysoft/blackbox/HelloRetry.java \
    app/src/main/java/org/guysoft/blackbox/KeyboardRouting.java \
    tests/org/guysoft/blackbox/KeyboardRoutingTest.java \
    tests/org/guysoft/blackbox/HelloRetryTest.java tests/org/guysoft/blackbox/ProtocolTest.java
java -cp build/host-tests org.guysoft.blackbox.ProtocolTest
java -cp build/host-tests org.guysoft.blackbox.HelloRetryTest
java -cp build/host-tests org.guysoft.blackbox.KeyboardRoutingTest
python3 tests/check_lifecycle.py
