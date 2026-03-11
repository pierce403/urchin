/*
 * P25 JSON output — emits one JSON line per decoded P25 event to stdout.
 * Output format matches SdrReading.P25 Kotlin data class fields.
 */

#ifndef JSON_OUT_H
#define JSON_OUT_H

#include <stdint.h>

typedef struct {
    char unit_id[16];
    char nac[8];
    char wacn[8];
    char system_id[8];
    char talkgroup[8];
    double rssi;
    int has_rssi;
    double freq;
    int has_freq;
} p25_event_t;

/* Initialize a p25_event_t to empty/default state */
void p25_event_init(p25_event_t *evt);

/* Emit JSON line to stdout. Only non-empty fields are included.
 * Requires unit_id to be non-empty; returns 0 on success, -1 if skipped. */
int p25_event_emit(const p25_event_t *evt);

#endif /* JSON_OUT_H */
