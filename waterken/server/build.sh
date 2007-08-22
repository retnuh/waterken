#!/bin/sh
if [ "$OS" = 'Windows_NT' ]
then
    CLASSPATH='..\joe-e\bin;..\ref_send\bin;..\network\bin;..\web_send\bin;..\persistence\bin;..\remote\bin'
else
    CLASSPATH='../joe-e/bin:../ref_send/bin:../network/bin:../web_send/bin:../persistence/bin:../remote/bin'
fi

echo 'Building server project...'
rm -rf bin/
mkdir -p bin
javac -classpath $CLASSPATH -d bin/ `find src/ -name '*.java'` $@
jar cmf SERVE.MF ../serve.jar X.class
jar cmf SHARE.MF ../share.jar X.class
