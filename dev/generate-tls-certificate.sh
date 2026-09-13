#!/bin/sh
set -eu
umask 077
if [ -e /certs/server.key ] || [ -e /certs/server.crt ]; then
    test -s /certs/server.key && test -s /certs/server.crt
    openssl x509 -in /certs/server.crt -checkend 0 -noout
else
    openssl req -x509 -newkey rsa:3072 -nodes -days 30 \
        -keyout /certs/server.key -out /certs/server.crt \
        -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
        -addext 'basicConstraints=critical,CA:FALSE' -addext 'extendedKeyUsage=serverAuth'
fi
chown -R 101:101 /certs
chmod 700 /certs
chmod 600 /certs/server.key
chmod 644 /certs/server.crt
