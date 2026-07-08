#!/bin/bash
# =============================================================================
# deploy.sh — Build e deploy do Fluxo de Caixa no Tomcat 10 (Ubuntu 24.04)
#
# Pré-requisitos no servidor:
#   - Java 17, Maven 3.x, Tomcat 10
#   - ojdbc8.jar em /opt/tomcat/lib/
#
# Uso:
#   ./deploy.sh [host]   # ex: ./deploy.sh root@app.lopesconsultores.com.br
#   ./deploy.sh          # build local + copia para Tomcat local
# =============================================================================

set -e

APP_NAME="fluxo-caixa"
WAR_FILE="target/${APP_NAME}.war"
TOMCAT_WEBAPPS="/opt/tomcat9/webapps"
TOMCAT_SERVICE="tomcat"      # ajuste se o serviço tiver outro nome
REMOTE_HOST="${1:-}"

echo "══════════════════════════════════════"
echo "  BUILD  — ${APP_NAME}"
echo "══════════════════════════════════════"
mvn clean package -DskipTests -q

echo ""
echo "══════════════════════════════════════"
echo "  DEPLOY  — ${WAR_FILE}"
echo "══════════════════════════════════════"

if [ -n "$REMOTE_HOST" ]; then
    # ── DEPLOY REMOTO ──────────────────────────────────────────────────────
    echo "▸ Copiando WAR para ${REMOTE_HOST}…"
    scp "${WAR_FILE}" "${REMOTE_HOST}:${TOMCAT_WEBAPPS}/${APP_NAME}.war"

    echo "▸ Reiniciando Tomcat no servidor…"
    ssh "${REMOTE_HOST}" "
        # Remove deploy anterior se existir
        rm -rf ${TOMCAT_WEBAPPS}/${APP_NAME}
        # Tomcat fará undeploy+redeploy automaticamente ao detectar o novo WAR
        # mas um restart garante limpeza de classloader
        systemctl restart ${TOMCAT_SERVICE}
        echo 'Aguardando Tomcat iniciar…'
        sleep 5
        systemctl status ${TOMCAT_SERVICE} --no-pager | grep -E 'Active|running'
    "
else
    # ── DEPLOY LOCAL ───────────────────────────────────────────────────────
    echo "▸ Copiando WAR para ${TOMCAT_WEBAPPS}…"
    sudo cp "${WAR_FILE}" "${TOMCAT_WEBAPPS}/${APP_NAME}.war"

    echo "▸ Reiniciando Tomcat local…"
    sudo systemctl restart "${TOMCAT_SERVICE}"
fi

echo ""
echo "✔  Deploy concluído!"
echo "   Acesse: http://localhost:8080/${APP_NAME}/"
echo "   API:    http://localhost:8080/${APP_NAME}/api/fluxo-realizado?dataIni=2026-06-01&dataFim=2026-06-30"
