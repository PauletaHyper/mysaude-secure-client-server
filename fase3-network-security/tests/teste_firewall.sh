#!/bin/bash
# ============================================================
#  TESTE FIREWALL — Correr no SERVIDOR
#  Trabalho 3, Parte I — Segurança Informática 2025/26
#
#  Uso: sudo ./tests/teste_firewall.sh
#
#  ANTES DE CORRER: preencher GCC_IP em firewall/iptables_mysaude.sh
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

OUTRO_IP="10.101.148.1"   # Gateway — IP fora da gcc para testar bloqueio R4
MYSAUDE_PORT=8080

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FIREWALL_SCRIPT="$SCRIPT_DIR/firewall/iptables_mysaude.sh"

pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; }
info() { echo -e "  ${YELLOW}[INFO]${NC} $1"; }
header() { echo -e "\n${BOLD}${CYAN}$1${NC}"; echo "$(printf '=%.0s' {1..60})"; }

ping_test() {
    local dest="$1" expect_ok="$2" label="$3"
    ping -c 1 -W 2 "$dest" &>/dev/null
    local result=$?
    if [ "$expect_ok" = "yes" ]; then
        [ $result -eq 0 ] && pass "$label — OK" || fail "$label — FALHOU (devia funcionar)"
    else
        [ $result -ne 0 ] && pass "$label — BLOQUEADO (esperado)" || fail "$label — passou (devia ser bloqueado)"
    fi
}

tcp_test() {
    local dest="$1" port="$2" expect_ok="$3" label="$4"
    nc -z -w 2 "$dest" "$port" &>/dev/null
    local result=$?
    if [ "$expect_ok" = "yes" ]; then
        [ $result -eq 0 ] && pass "$label — porta $port acessível" || fail "$label — porta $port inacessível (devia funcionar)"
    else
        [ $result -ne 0 ] && pass "$label — porta $port bloqueada (esperado)" || fail "$label — porta $port acessível (devia ser bloqueado)"
    fi
}

# ============================================================
#  PRÉ-VERIFICAÇÕES
# ============================================================
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Erro: correr com sudo.${NC}"
    exit 1
fi

# Ler o GCC_IP do próprio script de firewall (fonte única de verdade)
GCC_IP=$(grep '^GCC_IP=' "$FIREWALL_SCRIPT" | cut -d'"' -f2)
if [[ "$GCC_IP" =~ [A-Za-z] ]]; then
    echo -e "${RED}ERRO: GCC_IP não está preenchido em $FIREWALL_SCRIPT${NC}"
    echo "       Editar esse ficheiro e preencher: GCC_IP=\"10.101.X.X\""
    echo "       Obter o IP com: host gcc"
    exit 1
fi

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║     TESTE FIREWALL MYSAUDE — Trabalho 3, Parte I        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"
info "GCC_IP lido do script: $GCC_IP"

# ============================================================
#  PASSO 1 — ANTES DAS REGRAS: tudo deve ser permitido
# ============================================================
header "PASSO 1 — ANTES DAS REGRAS (tudo deve ser permitido)"

info "Estado atual das regras iptables:"
/sbin/iptables -L INPUT OUTPUT -n --line-numbers 2>/dev/null | head -20

echo ""
ping_test "$GCC_IP"   "yes" "Ping para gcc ($GCC_IP)"
ping_test "$OUTRO_IP" "yes" "Ping para Gateway ($OUTRO_IP)"
tcp_test "127.0.0.1" "$MYSAUDE_PORT" "yes" "Porta mySaude via loopback"

# ============================================================
#  PASSO 2 — ATIVAR AS REGRAS
# ============================================================
header "PASSO 2 — ATIVAR AS REGRAS"

info "A correr: $FIREWALL_SCRIPT"
bash "$FIREWALL_SCRIPT"

# ============================================================
#  PASSO 3 — VERIFICAR RESTRIÇÕES
# ============================================================
header "PASSO 3 — VERIFICAR RESTRIÇÕES"

echo -e "\n${YELLOW}R4 — Ping saída só para gcc:${NC}"
ping_test "$GCC_IP"   "yes" "R4: Ping para gcc ($GCC_IP)"
ping_test "$OUTRO_IP" "no"  "R4: Ping para Gateway ($OUTRO_IP)"
ping_test "8.8.8.8"   "no"  "R4: Ping para 8.8.8.8"

echo -e "\n${YELLOW}R3 — Porta mySaude acessível (via loopback):${NC}"
tcp_test "127.0.0.1" "$MYSAUDE_PORT" "yes" "R3: Porta 8080 via loopback"

echo -e "\n${YELLOW}R1/R2 — Requerem teste do lado do cliente:${NC}"
info "R1 (SSH só da gcc) e R2 (ping só da sub-rede): correr teste_snort.sh no cliente"

# ============================================================
#  PASSO 4 — CASOS AINDA PERMITIDOS
# ============================================================
header "PASSO 4 — CASOS AINDA PERMITIDOS"

ping_test "$GCC_IP" "yes" "R4: Ping para gcc continua a funcionar"
tcp_test "127.0.0.1" "$MYSAUDE_PORT" "yes" "R3: Porta 8080 continua acessível"

# ============================================================
#  RESUMO FINAL
# ============================================================
header "RESUMO — REGRAS FINAIS"
/sbin/iptables -L -v --line-numbers

echo ""
echo -e "${YELLOW}Para remover as regras após os testes:${NC}"
echo -e "  sudo $SCRIPT_DIR/firewall/remover_regras.sh"
echo ""
