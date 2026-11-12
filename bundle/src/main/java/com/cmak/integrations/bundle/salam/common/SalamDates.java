package com.cmak.integrations.bundle.salam.common;

import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.api.Value;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Converts a Remedy date value to the format ServiceNow import sets expect:
 * "yyyy-MM-dd HH:mm:ss".
 *
 * Remedy gives either an AR Timestamp (real date/time field) or a text such as
 * "2026-8-31T17:30:24" (non-padded, with a 'T' - this is what the old integration
 * sent to SALAM's Remedy, which was tolerant). ServiceNow is not.
 *
 * No time-zone shift is applied: the wall-clock time is kept as Remedy shows it.
 * (CONFIRM with SALAM which zone the import user is in; if they expect UTC, set
 * TARGET_ZONE below.)
 */
public final class SalamDates {
    private SalamDates() { }

    /** null = keep the server's own time zone (no conversion). */
    private static final TimeZone TARGET_ZONE = null;

    private static final String OUT = "yyyy-MM-dd HH:mm:ss";
    private static final String[] IN = {
            "yyyy-M-d'T'H:m:s", "yyyy-M-d H:m:s", "yyyy-M-d'T'H:m:sXXX", "yyyy-M-d'T'H:m:s.SSSXXX", "yyyy-M-d'T'H:m:sX", "yyyy-M-d'T'H:m:s.SSSX",
            "yyyy-M-d'T'H:m:s.SSS", "yyyy-M-d"
    };

    /** Entry.get(fieldId) returns a BMC Value wrapper; this returns the real object inside it. */
    public static Object unwrap(Object v) {
        return (v instanceof Value) ? ((Value) v).getValue() : v;
    }

    public static String toServiceNow(Object value) {
        value = unwrap(value);
        if (value == null) return "";
        if (value instanceof Timestamp) return format(((Timestamp) value).toDate());
        if (value instanceof Date) return format((Date) value);

        String s = value.toString().trim();
        if (s.isEmpty()) return "";
        for (String p : IN) {
            SimpleDateFormat f = new SimpleDateFormat(p);
            f.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date d = f.parse(s, pos);
            if (d != null && pos.getIndex() == s.length()) {
                // patterns without a zone are parsed in the JVM zone and re-printed in it: wall-clock kept
                return format(d);
            }
        }
        // unknown format: fail loudly rather than send something ServiceNow silently drops
        throw new IllegalArgumentException("Unrecognised date format from Remedy: '" + s + "'");
    }

    private static String format(Date d) {
        SimpleDateFormat f = new SimpleDateFormat(OUT);
        if (TARGET_ZONE != null) f.setTimeZone(TARGET_ZONE);
        return f.format(d);
    }
}
