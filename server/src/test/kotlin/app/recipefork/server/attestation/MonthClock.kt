package app.recipefork.server.attestation

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock set to a month, in UTC, that a test moves forward as the device's calendar would. */
internal class MonthClock(start: YearMonth) : Clock() {
    @Volatile
    var month: YearMonth = start

    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = month.atDay(15).atStartOfDay().toInstant(ZoneOffset.UTC)
}
