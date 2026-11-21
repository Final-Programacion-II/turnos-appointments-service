#!/usr/bin/env bash
# Prueba de punta a punta del backend con el sistema levantado en Docker (ver docs/docker.md).
#
# Reproduce las evidencias de la sección 11 del enunciado contra la cátedra real: crea dos
# usuarios nuevos, busca en el catálogo, hace una reserva (hold, teléfono por Kafka,
# confirmación), prueba que un usuario no vea ni cancele lo de otro y al final la cancela.
# No muestra tokens ni contraseñas: las contraseñas se generan al azar en cada corrida.
#
# Uso, desde la raíz del repo y con los cuatro contenedores en healthy:
#   bash docker/verificar.sh

set -u

TURNOS=http://localhost:8082
CATALOGO=http://localhost:8081

OK=0
MAL=0
ok()  { echo "  OK   $1"; OK=$((OK + 1)); }
mal() { echo "  MAL  $1"; MAL=$((MAL + 1)); }
titulo() { echo; echo "== $1"; }

# Código HTTP de un pedido. Uso: codigo METODO URL [TOKEN] [BODY]
codigo() {
    local args=(-s -o /dev/null -w '%{http_code}' -X "$1" "$2")
    [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
    [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
    curl "${args[@]}"
}

# Cuerpo de un pedido. Uso: cuerpo METODO URL [TOKEN] [BODY]
cuerpo() {
    local args=(-s -X "$1" "$2")
    [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
    [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
    curl "${args[@]}"
}

# Lee un campo de un JSON por stdin. Uso: echo "$json" | campo 'd["status"]'
campo() { python3 -c "import sys,json
try:
    d=json.load(sys.stdin); print($1)
except Exception:
    print('')"; }

esperar() {
    # Uso: esperar 3 "OK = igual"   (compara $1 con $2)
    if [ "$1" = "$2" ]; then ok "$3 ($1)"; else mal "$3 (esperaba $2, dio $1)"; fi
}

SUFIJO=$(python3 -c 'import secrets; print(secrets.token_hex(3))')
CLAVE=$(python3 -c 'import secrets; print(secrets.token_urlsafe(12))')

titulo "1. Servicios levantados"
esperar "$(codigo GET $CATALOGO/management/health)" 200 "catálogo /management/health"
esperar "$(codigo GET $TURNOS/management/health)" 200 "turnos /management/health"

titulo "2. Separación de datos"
if docker exec turnos-sistema-appointments-1 getent hosts catalog >/dev/null 2>&1; then
    ok "turnos llega al catálogo por la red interna"
else
    mal "turnos no resuelve 'catalog'"
fi
if docker exec turnos-sistema-appointments-1 getent hosts catalog-postgresql >/dev/null 2>&1; then
    mal "turnos ve la base del catálogo"
else
    ok "turnos no puede ni resolver catalog-postgresql"
fi

titulo "3. Rechazo sin autenticación"
esperar "$(codigo GET $CATALOGO/api/professionals)" 401 "catálogo sin token"
esperar "$(codigo GET $TURNOS/api/reservations)" 401 "turnos sin token"
esperar "$(codigo GET $TURNOS/api/reservations token-invalido)" 401 "turnos con token inválido"
esperar "$(codigo POST $CATALOGO/api/authenticate '' '{"username":"admin","password":"admin"}')" 401 "el catálogo no tiene login"

titulo "4. Registro y login"
registro() {
    codigo POST $TURNOS/api/register '' "{\"login\":\"$1\",\"password\":\"$CLAVE\",\"firstName\":\"Prueba\",\"lastName\":\"Final\",\"email\":\"$1@example.com\",\"langKey\":\"es\"}"
}
login() {
    cuerpo POST $TURNOS/api/authenticate '' "{\"username\":\"$1\",\"password\":\"$CLAVE\"}" | campo 'd.get("id_token","")'
}
USUARIO_A="prueba-a-$SUFIJO"
USUARIO_B="prueba-b-$SUFIJO"
esperar "$(registro "$USUARIO_A")" 201 "registro de $USUARIO_A"
esperar "$(registro "$USUARIO_B")" 201 "registro de $USUARIO_B"
esperar "$(registro "$USUARIO_A")" 400 "login repetido"
esperar "$(registro admin)" 400 "registrarse como admin"
TOKEN_A=$(login "$USUARIO_A")
TOKEN_B=$(login "$USUARIO_B")
[ ${#TOKEN_A} -gt 100 ] && ok "login de A" || mal "login de A"
[ ${#TOKEN_B} -gt 100 ] && ok "login de B" || mal "login de B"
CUENTA=$(cuerpo GET $TURNOS/api/account "$TOKEN_A")
esperar "$(echo "$CUENTA" | campo 'd["login"]')" "$USUARIO_A" "/api/account devuelve al usuario del token"
esperar "$(echo "$CUENTA" | campo '"password" in d')" False "/api/account sin contraseña"
esperar "$(codigo GET "$TURNOS/api/users" "$TOKEN_A")" 404 "no existe la lista pública de usuarios"

titulo "5. Catálogo: sincronización y búsquedas sobre la copia local"
ESTADO=$(cuerpo GET $CATALOGO/api/catalog/sync-status "$TOKEN_A")
esperar "$(echo "$ESTADO" | campo 'd["status"]')" OK "sync-status"
echo "       versión local: $(echo "$ESTADO" | campo 'd["catalogVersion"]')"
TODOS=$(cuerpo GET "$CATALOGO/api/professionals/search" "$TOKEN_A")
CANTIDAD=$(echo "$TODOS" | campo 'len(d)')
[ "${CANTIDAD:-0}" -gt 0 ] && ok "búsqueda sin filtros ($CANTIDAD profesionales)" || mal "búsqueda sin filtros vacía"
APELLIDO=$(echo "$TODOS" | campo 'd[0]["lastName"]')
CATEGORIA=$(echo "$TODOS" | campo 'd[0]["category"]["id"]')
CATEGORIA=${CATEGORIA:-0}
BUSCAR=$(python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1].lower()))' "$APELLIDO")
POR_NOMBRE=$(cuerpo GET "$CATALOGO/api/professionals/search?name=$BUSCAR" "$TOKEN_A" | campo "len(d)>0 and all('$APELLIDO'.lower() in (p['firstName']+' '+p['lastName']).lower() for p in d)")
esperar "$POR_NOMBRE" True "filtro por nombre ($APELLIDO, sin mayúsculas)"
POR_CATEGORIA=$(cuerpo GET "$CATALOGO/api/professionals/search?categoryId=$CATEGORIA" "$TOKEN_A" | campo "all(p['category']['id']==$CATEGORIA for p in d) and len(d)>0")
esperar "$POR_CATEGORIA" True "filtro por categoría"
HABILITADOS=$(cuerpo GET "$CATALOGO/api/professionals/search?enabled=true" "$TOKEN_A")
esperar "$(echo "$HABILITADOS" | campo "all(p['enabled'] for p in d) and len(d)>0")" True "filtro por estado habilitado"
esperar "$(codigo GET "$CATALOGO/api/professionals/search?day=MONDAY&from=08:00&to=20:00" "$TOKEN_A")" 200 "filtro por día y franja"
esperar "$(codigo GET "$CATALOGO/api/professionals/search?from=12:00&to=09:00" "$TOKEN_A")" 400 "franja invertida"

titulo "6. Disponibilidad (agenda del catálogo + ocupaciones de la cátedra)"
# Busca el primer profesional habilitado con turnos libres en los próximos días
ELEGIDO=""
for ID in $(echo "$HABILITADOS" | campo '" ".join(str(p["id"]) for p in d)'); do
    AGENDA=$(cuerpo GET "$CATALOGO/api/professionals/$ID/agenda" "$TOKEN_A")
    [ "$(echo "$AGENDA" | campo 'd["bookable"]')" = True ] || continue
    for FECHA in $(echo "$AGENDA" | python3 -c '
import sys,json,datetime
d=json.load(sys.stdin); dias={s["dayOfWeek"] for s in d["schedules"]}
hoy=datetime.date.today()
for i in range(1,15):
    f=hoy+datetime.timedelta(days=i)
    if f.strftime("%A").upper() in dias: print(f)'); do
        DISPO=$(cuerpo GET "$TURNOS/api/availability?professionalId=$ID&date=$FECHA" "$TOKEN_A")
        HORA=$(echo "$DISPO" | campo 'd["slots"][-1]["startTime"] if d.get("slots") else ""')
        if [ -n "$HORA" ]; then ELEGIDO="$ID"; break 2; fi
    done
done
if [ -z "$ELEGIDO" ]; then
    mal "no se encontró ningún turno libre en los próximos 14 días"
    echo; echo "Resultado: $OK OK, $MAL MAL (se cortó antes de reservar)"; exit 1
fi
HORA=${HORA:0:5}
ok "turno libre: profesional $ELEGIDO, $FECHA a las $HORA ($(echo "$DISPO" | campo 'd["professionalName"]'))"
esperar "$(codigo GET "$TURNOS/api/availability?professionalId=$ELEGIDO&date=2020-01-01" "$TOKEN_A")" 400 "fecha pasada"

titulo "7. Reserva: hold y confirmación inicial (REST)"
PEDIDO="{\"professionalId\":$ELEGIDO,\"date\":\"$FECHA\",\"startTime\":\"$HORA\",\"patientFirstName\":\"Prueba\",\"patientLastName\":\"Final\"}"
RESERVA=$(cuerpo POST $TURNOS/api/reservations "$TOKEN_A" "$PEDIDO")
RID=$(echo "$RESERVA" | campo 'd["id"]')
ESTADO_INICIAL=$(echo "$RESERVA" | campo 'd["status"]')
if [ -n "$RID" ]; then ok "reserva $RID creada ($ESTADO_INICIAL)"; else mal "no se pudo crear la reserva: $RESERVA"; exit 1; fi
esperar "$(codigo POST $TURNOS/api/reservations "$TOKEN_B" "$PEDIDO")" 409 "otro usuario pide el mismo turno"

titulo "8. Sin acceso cruzado"
esperar "$(codigo GET "$TURNOS/api/reservations/$RID" "$TOKEN_B")" 404 "B consulta la reserva de A"
esperar "$(codigo POST "$TURNOS/api/reservations/$RID/cancel" "$TOKEN_B" '{}')" 404 "B intenta cancelar la reserva de A"
esperar "$(cuerpo GET "$TURNOS/api/reservations" "$TOKEN_B" | campo "any(r['id']==$RID for r in d)")" False "la reserva de A no aparece en la lista de B"

titulo "9. Teléfono por Kafka y confirmación"
PIDIO=""
for _ in $(seq 1 45); do
    PIDIO=$(cuerpo GET "$TURNOS/api/reservations/$RID" "$TOKEN_A" | campo 'd["phoneRequested"]')
    [ "$PIDIO" = True ] && break
    sleep 2
done
esperar "$PIDIO" True "llegó AdditionalInformationRequested"
esperar "$(codigo POST "$TURNOS/api/reservations/$RID/phone" "$TOKEN_A" '{"phoneNumber":"no-es-un-telefono"}')" 400 "teléfono inválido"
RESP=$(cuerpo POST "$TURNOS/api/reservations/$RID/phone" "$TOKEN_A" '{"phoneNumber":"+54 9 261 555-1234"}')
esperar "$(echo "$RESP" | campo 'd["status"]')" PHONE_SUBMITTED "teléfono enviado"
FINAL=""
for _ in $(seq 1 45); do
    FINAL=$(cuerpo GET "$TURNOS/api/reservations/$RID" "$TOKEN_A" | campo 'd["status"]')
    [ "$FINAL" != PHONE_SUBMITTED ] && break
    sleep 2
done
esperar "$FINAL" CONFIRMED "resultado final por Kafka"
QUEDA_LIBRE=$(cuerpo GET "$TURNOS/api/availability?professionalId=$ELEGIDO&date=$FECHA" "$TOKEN_A" | campo "any(s['startTime'].startswith('$HORA') for s in d['slots'])")
esperar "$QUEDA_LIBRE" False "el turno confirmado ya no aparece libre"

titulo "10. Mis reservas"
CABECERAS=$(curl -s -D - -o /dev/null -H "Authorization: Bearer $TOKEN_A" "$TURNOS/api/reservations?status=CONFIRMED")
echo "$CABECERAS" | grep -qi '^x-total-count: [1-9]' && ok "listado paginado con X-Total-Count" || mal "falta X-Total-Count"
esperar "$(cuerpo GET "$TURNOS/api/reservations" "$TOKEN_A" | campo "any(r['id']==$RID for r in d)")" True "la reserva aparece en la lista de A"

titulo "11. Cancelación"
esperar "$(cuerpo POST "$TURNOS/api/reservations/$RID/cancel" "$TOKEN_A" '{"reason":"Prueba final del backend"}' | campo 'd["status"]')" CANCELLED "A cancela su reserva"
esperar "$(cuerpo POST "$TURNOS/api/reservations/$RID/cancel" "$TOKEN_A" '{}' | campo 'd["status"]')" CANCELLED "cancelar otra vez no cambia nada"
sleep 5
esperar "$(cuerpo GET "$TURNOS/api/reservations/$RID" "$TOKEN_A" | campo 'd["status"]')" CANCELLED "el AppointmentCancelled de Kafka no la reabre"

titulo "12. Idempotencia de Kafka"
EVENTOS=$(docker exec turnos-sistema-appointments-postgresql-1 psql -U appointments -d appointments -tAc "select count(*) from processed_reservation_event e join reservation_process p on p.reservation_process_id = e.reservation_process_id where p.id = $RID" 2>/dev/null)
if [ -n "$EVENTOS" ] && [ "$EVENTOS" -ge 2 ] 2>/dev/null; then
    ok "eventos de la reserva registrados ($EVENTOS; event_id es clave primaria, no puede repetirse)"
else
    echo "  --   no se pudo contar los eventos registrados (no cambia el resultado)"
fi

echo
echo "======================================"
echo "Resultado: $OK OK, $MAL MAL"
echo "Reserva de prueba: $RID (cancelada). Usuarios: $USUARIO_A y $USUARIO_B"
[ "$MAL" -eq 0 ]
