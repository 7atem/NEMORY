package com.vaultbrain.core.integrations.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface CalendarDataSource {
    fun isAvailable(): Boolean
    fun hasReadPermission(): Boolean
    fun selectedCalendarIds(): Set<Long>
    fun setSelectedCalendarIds(ids: Set<Long>)
    suspend fun calendars(): List<CalendarInfo>
    suspend fun events(calendarIds: Set<Long>, startAt: Long, endAt: Long): List<CalendarEventRow>
    fun open(uri: String): Boolean
    fun createEvent(draft: CalendarEventDraft): Boolean
}

@Singleton
class AndroidCalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarDataSource {
    private val resolver: ContentResolver get() = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun isAvailable(): Boolean =
        context.packageManager.resolveContentProvider(CalendarContract.AUTHORITY, 0) != null

    override fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    override fun selectedCalendarIds(): Set<Long> =
        preferences.getStringSet(KEY_SELECTED, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()

    override fun setSelectedCalendarIds(ids: Set<Long>) {
        preferences.edit().putStringSet(KEY_SELECTED, ids.map(Long::toString).toSet()).apply()
    }

    override suspend fun calendars(): List<CalendarInfo> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE
        )
        return runCatching {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CalendarInfo(
                                id = cursor.getLong(0),
                                name = cursor.getString(1)?.takeIf(String::isNotBlank)
                                    ?: cursor.getString(2)?.takeIf(String::isNotBlank)
                                    ?: "Calendar",
                                accountName = cursor.getString(3),
                                isVisible = cursor.getInt(4) != 0
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override suspend fun events(
        calendarIds: Set<Long>,
        startAt: Long,
        endAt: Long
    ): List<CalendarEventRow> {
        if (!hasReadPermission() || calendarIds.isEmpty()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startAt)
            ContentUris.appendId(it, endAt)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DELETED
        )
        val placeholders = calendarIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        return resolver.query(
            uri,
            projection,
            selection,
            calendarIds.map(Long::toString).toTypedArray(),
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CalendarEventRow(
                            eventId = cursor.getLong(0),
                            calendarId = cursor.getLong(1),
                            title = cursor.getString(2),
                            description = cursor.getString(3),
                            location = cursor.getString(4),
                            startAt = cursor.getLong(5),
                            endAt = cursor.getLong(6),
                            allDay = cursor.getInt(7) != 0,
                            recurrenceRule = cursor.getString(8),
                            timezone = cursor.getString(9),
                            deleted = cursor.getInt(10) != 0
                        )
                    )
                }
            }
        }.orEmpty()
    }

    override fun open(uri: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    override fun createEvent(draft: CalendarEventDraft): Boolean = runCatching {
        val intent = CalendarIntentFactory.createInsertIntent(draft)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        private const val PREFERENCES = "calendar_connector"
        private const val KEY_SELECTED = "selected_calendar_ids"
    }
}

object CalendarIntentFactory {
    fun createInsertIntent(draft: CalendarEventDraft): Intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, draft.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, draft.description)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.startAt)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endAt)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, draft.allDay)
}
