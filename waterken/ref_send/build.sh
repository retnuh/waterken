#!/bin/sh

echo 'Building ref_send project...'
rm -rf bin/*
javac -classpath "../joe-e/bin" -d bin/ `find src/ -name '*.java'` $@
