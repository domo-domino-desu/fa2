package me.domino.fa2.ui.pages.search.util

import kotlin.time.Clock

private const val millisPerDay = 86_400_000L
private val minSearchEpochDay = epochDayFromCivil(2005, 12, 4)

internal const val minSearchDateIso = "2005-12-04"

internal data class SearchDateBounds(
    val minEpochDay: Long,
    val maxEpochDay: Long,
) {
  val minIsoDate: String
    get() = epochDayToIsoDate(minEpochDay)

  val maxIsoDate: String
    get() = epochDayToIsoDate(maxEpochDay)
}

internal data class SearchDateFields(
    val from: String,
    val to: String,
)

enum class SearchDateRangeShiftAction {
  PreviousYear,
  PreviousMonth,
  PreviousDay,
  NextDay,
  NextMonth,
  NextYear,
}

internal fun epochMillisToIsoDate(epochMillis: Long): String {
  val epochDay = floorDiv(epochMillis, millisPerDay)
  return epochDayToIsoDate(epochDay)
}

internal fun epochDayToIsoDate(epochDay: Long): String {
  val (year, month, day) = civilFromEpochDay(epochDay)
  return buildString {
    append(year.toString().padStart(4, '0'))
    append('-')
    append(month.toString().padStart(2, '0'))
    append('-')
    append(day.toString().padStart(2, '0'))
  }
}

internal fun isoDateToEpochMillisOrNull(value: String): Long? {
  val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value.trim()) ?: return null
  val year = match.groupValues[1].toIntOrNull() ?: return null
  val month = match.groupValues[2].toIntOrNull() ?: return null
  val day = match.groupValues[3].toIntOrNull() ?: return null
  if (month !in 1..12 || day !in 1..31) return null
  val epochDay = epochDayFromCivil(year, month, day)
  return epochDay * millisPerDay
}

internal fun isoDateToEpochDayOrNull(value: String): Long? =
    isoDateToEpochMillisOrNull(value)?.let { floorDiv(it, millisPerDay) }

internal fun currentSearchDateBounds(
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): SearchDateBounds {
  val todayEpochDay = floorDiv(nowEpochMillis, millisPerDay)
  return SearchDateBounds(
      minEpochDay = minSearchEpochDay,
      maxEpochDay = todayEpochDay + 1L,
  )
}

internal fun normalizeManualDateFields(
    rangeFrom: String,
    rangeTo: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): SearchDateFields {
  val bounds = currentSearchDateBounds(nowEpochMillis)
  val fromEpochDay =
      isoDateToEpochDayOrNull(rangeFrom)?.coerceIn(bounds.minEpochDay, bounds.maxEpochDay)
  val toEpochDay =
      isoDateToEpochDayOrNull(rangeTo)?.coerceIn(bounds.minEpochDay, bounds.maxEpochDay)
  return when {
    fromEpochDay != null && toEpochDay != null -> {
      val normalizedFrom = minOf(fromEpochDay, toEpochDay)
      val normalizedTo = maxOf(fromEpochDay, toEpochDay)
      SearchDateFields(
          from = epochDayToIsoDate(normalizedFrom),
          to = epochDayToIsoDate(normalizedTo),
      )
    }

    fromEpochDay != null -> SearchDateFields(from = epochDayToIsoDate(fromEpochDay), to = "")

    toEpochDay != null -> SearchDateFields(from = "", to = epochDayToIsoDate(toEpochDay))

    else -> SearchDateFields(from = "", to = "")
  }
}

internal fun resolveSearchDateFields(
    range: String,
    rangeFrom: String,
    rangeTo: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): SearchDateFields? {
  if (range == "all") return null
  if (range == "manual") {
    val normalized = normalizeManualDateFields(rangeFrom, rangeTo, nowEpochMillis)
    return normalized.takeIf { it.from.isNotBlank() && it.to.isNotBlank() }
  }
  val bounds = currentSearchDateBounds(nowEpochMillis)
  val maxEpochDay = bounds.maxEpochDay
  val fromEpochDay =
      when (range) {
        "1day" -> maxEpochDay - 1L
        "3days" -> maxEpochDay - 3L
        "7days" -> maxEpochDay - 7L
        "30days" -> maxEpochDay - 30L
        "90days" -> maxEpochDay - 90L
        "1year" -> shiftEpochDayByMonths(maxEpochDay, -12)
        "3years" -> shiftEpochDayByMonths(maxEpochDay, -36)
        "5years" -> shiftEpochDayByMonths(maxEpochDay, -60)
        else -> return null
      }.coerceAtLeast(bounds.minEpochDay)
  return SearchDateFields(
      from = epochDayToIsoDate(fromEpochDay),
      to = epochDayToIsoDate(maxEpochDay),
  )
}

internal fun shiftSearchDateFields(
    fields: SearchDateFields,
    action: SearchDateRangeShiftAction,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): SearchDateFields {
  val bounds = currentSearchDateBounds(nowEpochMillis)
  val currentFrom = isoDateToEpochDayOrNull(fields.from) ?: return fields
  val currentTo = isoDateToEpochDayOrNull(fields.to) ?: return fields
  val shiftedFrom =
      when (action) {
        SearchDateRangeShiftAction.PreviousYear -> shiftEpochDayByMonths(currentFrom, -12)
        SearchDateRangeShiftAction.PreviousMonth -> shiftEpochDayByMonths(currentFrom, -1)
        SearchDateRangeShiftAction.PreviousDay -> currentFrom - 1L
        SearchDateRangeShiftAction.NextDay -> currentFrom + 1L
        SearchDateRangeShiftAction.NextMonth -> shiftEpochDayByMonths(currentFrom, 1)
        SearchDateRangeShiftAction.NextYear -> shiftEpochDayByMonths(currentFrom, 12)
      }
  val shiftedTo =
      when (action) {
        SearchDateRangeShiftAction.PreviousYear -> shiftEpochDayByMonths(currentTo, -12)
        SearchDateRangeShiftAction.PreviousMonth -> shiftEpochDayByMonths(currentTo, -1)
        SearchDateRangeShiftAction.PreviousDay -> currentTo - 1L
        SearchDateRangeShiftAction.NextDay -> currentTo + 1L
        SearchDateRangeShiftAction.NextMonth -> shiftEpochDayByMonths(currentTo, 1)
        SearchDateRangeShiftAction.NextYear -> shiftEpochDayByMonths(currentTo, 12)
      }
  val duration = currentTo - currentFrom
  val normalized =
      when {
        shiftedFrom < bounds.minEpochDay -> {
          val adjustedFrom = bounds.minEpochDay
          val adjustedTo = (adjustedFrom + duration).coerceAtMost(bounds.maxEpochDay)
          SearchDateFields(epochDayToIsoDate(adjustedFrom), epochDayToIsoDate(adjustedTo))
        }

        shiftedTo > bounds.maxEpochDay -> {
          val adjustedTo = bounds.maxEpochDay
          val adjustedFrom = (adjustedTo - duration).coerceAtLeast(bounds.minEpochDay)
          SearchDateFields(epochDayToIsoDate(adjustedFrom), epochDayToIsoDate(adjustedTo))
        }

        else -> SearchDateFields(epochDayToIsoDate(shiftedFrom), epochDayToIsoDate(shiftedTo))
      }
  return normalizeManualDateFields(normalized.from, normalized.to, nowEpochMillis)
}

internal fun canShiftSearchDateFields(
    fields: SearchDateFields?,
    action: SearchDateRangeShiftAction,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): Boolean {
  if (fields == null) return false
  return shiftSearchDateFields(fields, action, nowEpochMillis) != fields
}

private fun shiftEpochDayByMonths(epochDay: Long, monthsDelta: Int): Long {
  val (year, month, day) = civilFromEpochDay(epochDay)
  var totalMonths = year.toLong() * 12L + (month - 1).toLong() + monthsDelta.toLong()
  var shiftedYear = floorDiv(totalMonths, 12L).toInt()
  var shiftedMonth = ((totalMonths % 12L + 12L) % 12L).toInt() + 1
  if (shiftedMonth <= 0) {
    shiftedMonth += 12
    shiftedYear -= 1
  }
  val shiftedDay = minOf(day, daysInMonth(shiftedYear, shiftedMonth))
  return epochDayFromCivil(shiftedYear, shiftedMonth, shiftedDay)
}

private fun daysInMonth(year: Int, month: Int): Int =
    when (month) {
      1,
      3,
      5,
      7,
      8,
      10,
      12 -> 31
      4,
      6,
      9,
      11 -> 30
      2 -> if (isLeapYear(year)) 29 else 28
      else -> 30
    }

private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private fun floorDiv(a: Long, b: Long): Long {
  val q = a / b
  val r = a % b
  return if (r == 0L || a >= 0) q else q - 1
}

private fun civilFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
  val z = epochDay + 719468L
  val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
  val doe = z - era * 146097
  val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
  var y = yoe + era * 400
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val d = doy - (153 * mp + 2) / 5 + 1
  val m = mp + if (mp < 10) 3 else -9
  if (m <= 2) y += 1
  return Triple(y.toInt(), m.toInt(), d.toInt())
}

private fun epochDayFromCivil(year: Int, month: Int, day: Int): Long {
  var y = year.toLong()
  val m = month.toLong()
  val d = day.toLong()
  y -= if (m <= 2L) 1L else 0L
  val era = if (y >= 0) y / 400 else (y - 399) / 400
  val yoe = y - era * 400
  val mp = m + if (m > 2L) -3L else 9L
  val doy = (153 * mp + 2) / 5 + d - 1
  val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
  return era * 146097 + doe - 719468
}
