package me.domino.fa2.ui.screen.search.util

private const val millisPerDay = 86_400_000L

internal fun epochMillisToIsoDate(epochMillis: Long): String {
  val epochDay = floorDiv(epochMillis, millisPerDay)
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
