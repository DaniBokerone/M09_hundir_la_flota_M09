#!/bin/bash

# Variables per defecte (es poden sobreescriure passant arguments)
DEFAULT_USER="nomUsuari"
DEFAULT_RSA_PATH="$HOME/Desktop/Proxmox IETI/id_rsa"
DEFAULT_SERVER_PORT="3000"

USER="${1:-$DEFAULT_USER}"
RSA_PATH=${2:-$DEFAULT_RSA_PATH}
SERVER_PORT="${3:-$DEFAULT_SERVER_PORT}"

JAR_PATH="./target/server-package.jar"

# Comprovem que els arxius existeixen
if [[ ! -f "$RSA_PATH" ]]; then
  echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
  exit 1
fi

# Generar '.jar'
rm -f JAR_PATH
./run.sh com.project.Server build

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Error: No s'ha trobat l'arxiu JAR: $JAR_PATH"
  exit 1
fi

# Iniciar ssh-agent i carregar la clau RSA
eval "$(ssh-agent -s)"
ssh-add "$RSA_PATH"

# Enviament SCP del JAR al servidor
scp -P 20127 "$JAR_PATH" "$USER@ieticloudpro.ieti.cat:~/"
if [[ $? -ne 0 ]]; then
  echo "Error durant l'enviament SCP"
  exit 1
fi

# SSH al servidor per fer executar el JAR
ssh -t -p 20127 "$USER@ieticloudpro.ieti.cat" << EOF
    cd "\$HOME/"
    nohup java -jar server-package.jar > output.log 2>&1 &
    exit
EOF

# Finalitzar l'agent SSH
ssh-agent -k
