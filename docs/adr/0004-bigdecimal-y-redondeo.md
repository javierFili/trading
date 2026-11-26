# ADR-0004: BigDecimal y política de redondeo

- Estado: Aceptada (Fase 0)

## Decisión
- Todos los precios, cantidades e importes son `BigDecimal`; en BD `NUMERIC(38,18)`.
- Cálculos intermedios con `MathContext.DECIMAL128`; redondeo a precisión del exchange solo en la frontera.
- Cantidades redondeadas **hacia abajo** al `stepSize` (nunca superar el riesgo); precios al `tickSize` en la dirección conservadora según el lado.
- Comparaciones con `compareTo`, nunca `equals` (que considera la escala).
- `float`/`double` prohibidos en módulos financieros (ArchUnit, condición propia que inspecciona campos, firmas y llamadas).

## Consecuencias
+ Exactitud y reproducibilidad. − Algo más de verbosidad y coste de CPU, irrelevante a 1 h de temporalidad.
