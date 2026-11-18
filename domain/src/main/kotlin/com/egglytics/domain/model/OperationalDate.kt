package com.egglytics.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object OperationalDate {
    val BOGOTA_ZONE: ZoneId = ZoneId.of("America/Bogota")

    private val STRICT_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val STRICT_FORMATTER: DateTimeFormatter = DateTimeFormatter
        .ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)

    fun parse(value: String): LocalDate {
        require(STRICT_PATTERN.matches(value)) {
            "Formato de fecha operativa inválido (esperado YYYY-MM-DD): '$value'"
        }
        return try {
            LocalDate.parse(value, STRICT_FORMATTER)
        } catch (ex: DateTimeParseException) {
            throw IllegalArgumentException("Fecha operativa inválida en calendario: '$value'", ex)
        }
    }

    fun format(date: LocalDate): String = date.format(STRICT_FORMATTER)

    fun today(clock: Clock): LocalDate = Instant.now(clock).atZone(BOGOTA_ZONE).toLocalDate()
}
