# Tiempo Extra

App personal para pronosticar el **rol de guardias de tiempo extra**: cuándo te va a tocar, qué fechas conviene evitar y cuándo sí (o no) apuntarse de voluntario.

Es una sola página (`index.html`) sin servidor: ábrela en el teléfono o en la computadora y los datos se guardan en el navegador. Exporta un respaldo JSON desde **Ajustes** de vez en cuando.

## Cómo funciona el rol (reglas que modela la app)

- Lista ordenada por antigüedad; el puntero asigna **de abajo (más nuevo) hacia arriba**.
- Cada columna del rol es una **vuelta** completa.
- Cuando sale un oficio, hay plazas por fecha y turno. Primero se anotan **voluntarios** (hasta 3 días antes); las plazas que quedan vacías se llenan con el puntero (**obligados**).
- En el rol solo se anota a quien fue **obligado**, estaba de **vacaciones** o en **curso** (también incapacidad). Ser voluntario **no** cuenta ni mueve tu lugar.
- Rechazar cuando te toca cuesta una semana de productividad y te lo cobran en la siguiente.

## Estrategia que recomienda la app

1. Si vas 1.º o 2.º en la fila, **no te apuntes de voluntario**: déjate obligar en una fecha aceptable y quedas protegido toda la vuelta.
2. Si vas lejos, ser voluntario es neutro: apúntate solo cuando te convenga.
3. Si tu plaza cae en una fecha que quieres evitar, el único escudo real son **vacaciones o curso** ese día.

## Pestañas

- **Panel**: plazas forzadas por delante, fecha estimada en que te obligan, alertas y recomendaciones, fila del puntero, simulación de los próximos 12 meses y fechas límite de voluntarios.
- **Rol**: la cuadrícula de vueltas (toca una celda para capturar O/V/C/I), altas, bajas y orden de antigüedad.
- **Oficios**: captura cada oficio (fecha, plazas de tu lista, voluntarios anotados).
- **Vacaciones**: periodos conocidos de cada compañero; si el puntero llega a alguien que está fuera, su plaza se quema sola.
- **Ajustes**: quién eres, festivos/fechas a evitar, parámetros de estimación, exportar/importar.

## Estimación

Para cada fecha futura (oficios capturados + festivos del calendario mexicano) se calcula cuántos obligados habrá: si hay oficio, `plazas − voluntarios`; si no, el promedio histórico de obligados en ese festivo según el rol (o un valor por defecto). Luego se recorre la fila del puntero saltando a quien esté de vacaciones o curso.
