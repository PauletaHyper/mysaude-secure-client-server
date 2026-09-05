#!/bin/bash
# ============================================================
#  SETUP COMPLETO DO SERVIDOR — Segunda + Terceira Fase
#  Segurança Informática 2025/26
#
#  Correr UMA VEZ antes de arrancar o servidor.
#  Cria keystores, certificados e utilizadores.
#
#  Uso: ./setup_servidor.sh
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

TERCEIRA_FASE="$(cd "$(dirname "$0")" && pwd)"
SEGUNDA_FASE="$(cd "$(dirname "$0")/../Segunda Fase" && pwd)"
KS_PASS=123456
MAC_PASS=macpassword123

info()  { echo -e "  ${YELLOW}[INFO]${NC} $1"; }
ok()    { echo -e "  ${GREEN}[ OK ]${NC} $1"; }
erro()  { echo -e "  ${RED}[ERRO]${NC} $1"; }
header(){ echo -e "\n${BOLD}${CYAN}$1${NC}"; echo "$(printf '=%.0s' {1..60})"; }

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║        SETUP SERVIDOR — MySaude + Firewall + IDS        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ============================================================
#  VERIFICAÇÕES INICIAIS
# ============================================================
header "VERIFICAÇÕES"

if [ ! -d "$SEGUNDA_FASE" ]; then
    erro "Pasta 'Segunda Fase' não encontrada em: $SEGUNDA_FASE"
    exit 1
fi
ok "Pasta Segunda Fase encontrada"

cd "$SEGUNDA_FASE"

java -version &>/dev/null && ok "Java instalado" || { erro "Java não encontrado"; exit 1; }
which keytool &>/dev/null && ok "keytool disponível" || { erro "keytool não encontrado"; exit 1; }

# ============================================================
#  COMPILAR (FASE 2)
# ============================================================
header "FASE 2 — COMPILAR"

javac -encoding UTF-8 \
    server/PasswordManager.java server/MacManager.java \
    server/CriarUser.java server/MySaudeServer.java \
    client/KeyUtils.java client/CryptoUtils.java client/MySaude.java 2>&1

[ $? -eq 0 ] && ok "Compilação OK" || { erro "Compilação falhou"; exit 1; }

# ============================================================
#  CRIAR KEYSTORES (FASE 2)
# ============================================================
header "FASE 2 — KEYSTORES E CERTIFICADOS"

for user in usera userb userc userd; do
    if [ -f "keystore.$user" ]; then
        info "keystore.$user já existe — a saltar"
    else
        keytool -genkeypair -alias "$user" -keyalg RSA -keysize 2048 \
            -storetype JKS -keystore "keystore.$user" -validity 365 \
            -storepass $KS_PASS -keypass $KS_PASS -dname "CN=$user" 2>/dev/null \
            && ok "keystore.$user criada" || erro "Falha ao criar keystore.$user"
    fi
done

# Exportar certificados
for user in usera userb userc userd; do
    keytool -exportcert -alias "$user" -keystore "keystore.$user" \
        -file "$user.cer" -storepass $KS_PASS 2>/dev/null \
        && ok "Certificado $user.cer exportado" || erro "Falha ao exportar $user.cer"
done

# Importar relações de confiança
info "A importar certificados nas keystores..."
keytool -importcert -alias userb      -file userb.cer      -keystore keystore.usera    -storepass $KS_PASS -noprompt 2>/dev/null
keytool -importcert -alias userc    -file userc.cer    -keystore keystore.usera    -storepass $KS_PASS -noprompt 2>/dev/null
keytool -importcert -alias usera    -file usera.cer    -keystore keystore.userb      -storepass $KS_PASS -noprompt 2>/dev/null
keytool -importcert -alias usera    -file usera.cer    -keystore keystore.userc    -storepass $KS_PASS -noprompt 2>/dev/null
keytool -importcert -alias usera    -file usera.cer    -keystore keystore.userd -storepass $KS_PASS -noprompt 2>/dev/null
ok "Relações de confiança importadas"

# ============================================================
#  CRIAR UTILIZADORES (FASE 2)
# ============================================================
header "FASE 2 — CRIAR UTILIZADORES"

if [ -f "server_storage/users" ]; then
    info "Utilizadores já existem (server_storage/users). A saltar."
    info "Conteúdo atual:"
    cat server_storage/users
else
    echo $MAC_PASS | java server.CriarUser usera    medico $KS_PASS -f usera.cer    2>/dev/null && ok "usera (medico)"    || erro "Falha usera"
    echo $MAC_PASS | java server.CriarUser userb      medico $KS_PASS -f userb.cer      2>/dev/null && ok "userb (medico)"      || erro "Falha userb"
    echo $MAC_PASS | java server.CriarUser userc    medico $KS_PASS -f userc.cer    2>/dev/null && ok "userc (medico)"    || erro "Falha userc"
    echo $MAC_PASS | java server.CriarUser bob       utente $KS_PASS -f userd.cer 2>/dev/null && ok "bob (utente)"      || erro "Falha bob"
    echo ""
    info "Ficheiro users criado:"
    cat server_storage/users
fi

# ============================================================
#  RESUMO FINAL
# ============================================================
header "SETUP CONCLUÍDO"

echo ""
echo -e "${BOLD}Agora abrir 3 terminais no SERVIDOR:${NC}"
echo ""
echo -e "${CYAN}TERMINAL 1 — Servidor mySaude (Fase 2):${NC}"
echo "  cd \"$SEGUNDA_FASE\""
echo "  export _JAVA_OPTIONS=\"-Djavax.net.ssl.trustStore=keystore.usera -Djavax.net.ssl.trustStorePassword=$KS_PASS\""
echo "  echo $MAC_PASS | java server.MySaudeServer 8080 keystore.usera $KS_PASS"
echo ""
echo -e "${CYAN}TERMINAL 2 — Firewall iptables (Fase 3) — após servidor arrancar:${NC}"
echo "  sudo \"$TERCEIRA_FASE/firewall/iptables_mysaude.sh\""
echo ""
echo -e "${CYAN}TERMINAL 3 — Snort IDS (Fase 3) — após servidor arrancar:${NC}"
echo "  cd \"$TERCEIRA_FASE/ids\""
echo "  sudo snort -i \$(ip route | grep default | awk '{print \$5}' | head -1) -A console -q -c mysaude_snort.conf"
echo ""
echo -e "${BOLD}No CLIENTE:${NC}"
echo "  export _JAVA_OPTIONS=\"-Djavax.net.ssl.trustStore=keystore.usera -Djavax.net.ssl.trustStorePassword=$KS_PASS\""
echo "  \"$TERCEIRA_FASE/tests/teste_snort.sh\" <IP_SERVIDOR>"
echo ""
