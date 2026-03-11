/*
 * P25 JSON output
 *
 * Emits newline-delimited JSON to stdout, one object per P25 event.
 * Format is compatible with P25NetworkBridge.parseP25Json() on the Kotlin side.
 */

#include "json_out.h"
#include <stdio.h>
#include <string.h>

void p25_event_init(p25_event_t *evt) {
    memset(evt, 0, sizeof(*evt));
}

int p25_event_emit(const p25_event_t *evt) {
    if (evt->unit_id[0] == '\0') {
        return -1;
    }

    /* Build JSON manually to avoid external dependencies */
    printf("{\"unit_id\":\"%s\"", evt->unit_id);

    if (evt->nac[0] != '\0') {
        printf(",\"nac\":\"%s\"", evt->nac);
    }
    if (evt->wacn[0] != '\0') {
        printf(",\"wacn\":\"%s\"", evt->wacn);
    }
    if (evt->system_id[0] != '\0') {
        printf(",\"system_id\":\"%s\"", evt->system_id);
    }
    if (evt->talkgroup[0] != '\0') {
        printf(",\"talkgroup\":\"%s\"", evt->talkgroup);
    }
    if (evt->has_rssi) {
        printf(",\"rssi\":%.1f", evt->rssi);
    }
    if (evt->has_freq) {
        printf(",\"freq\":%.4f", evt->freq);
    }

    printf("}\n");
    fflush(stdout);
    return 0;
}
