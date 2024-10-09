#!/bin/bash

# Variables per defecte (es poden sobreescriure passant arguments)
DEFAULT_USER="nomUsuari"
DEFAULT_RSA_PATH="$HOME/Desktop/Proxmox IETI/id_rsa"

USER="${1:-$DEFAULT_USER}"
RSA_PATH=${2:-$DEFAULT_RSA_PATH}

JAR_NAME="server-package.jar"s

# Comprovem que els arxius existeixen
if [[ ! -f "$RSA_PATH" ]]; then
  echo "Error: No s'ha trobat el fitxer de clau privada: $RSA_PATH"
  exit 1
fi

# Iniciar ssh-agent i carregar la clau RSA
eval "$(ssh-agent -s)"
ssh-add "$RSA_PATH"

# SSH al servidor per trobar i matar el procés del JAR
ssh -t -p 20127 "$USER@ieticloudpro.ieti.cat" << EOF
  PID=\$(ps aux | grep 'java -jar $JAR_NAME' | grep -v 'grep' | awk '{print \$2}')
  if [ -n "\$PID" ]; then
    # Mata el procés si es troba
    kill \$PID
    echo "Procés $JAR_NAME amb PID \$PID aturat."
  else
    echo "No s'ha trobat el procés $JAR_NAME."
  fi
EOF

# Finalitzar l'agent SSH
ssh-agent -k
