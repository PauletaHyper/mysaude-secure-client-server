#!/bin/bash
# ============================================================
#  Remove todas as regras iptables e repõe políticas ACCEPT
#  Usar após os testes para não ficar sem acesso à máquina
# ============================================================

/sbin/iptables -F
/sbin/iptables -X
/sbin/iptables -t nat    -F 2>/dev/null
/sbin/iptables -t mangle -F 2>/dev/null

/sbin/iptables -P INPUT   ACCEPT
/sbin/iptables -P OUTPUT  ACCEPT
/sbin/iptables -P FORWARD ACCEPT

echo "Regras removidas. Políticas reposta para ACCEPT."
/sbin/iptables -L -v --line-numbers
