/*
 * P25 Phase 1 Frame Parser
 *
 * Handles frame synchronization, type classification, and metadata extraction
 * from P25 C4FM dibit streams.
 *
 * P25 frame types carrying metadata:
 *   - NID:  Network ID in every frame header (NAC, DUID)
 *   - TSBK: Trunking Signaling Block (unit grants, registrations)
 *   - HDU:  Header Data Unit (source unit ID)
 *   - TDU:  Terminator Data Unit (link control / talk group)
 *   - LDU:  Embedded link control words (unit/group context)
 */

#ifndef P25_FRAME_H
#define P25_FRAME_H

#include <stdint.h>
#include "json_out.h"

/* P25 frame sync pattern: 48 dibits (96 bits)
 * Frame sync: 0x5575F5FF77FF */
#define P25_SYNC_LEN   24   /* 24 dibits = 48 bits */

/* Data Unit IDs (DUID) — from NID field */
#define DUID_HDU   0x0  /* Header Data Unit */
#define DUID_TDU   0x3  /* Simple Terminator Data Unit */
#define DUID_LDU1  0x5  /* Logical Link Data Unit 1 */
#define DUID_TSDU  0x7  /* Trunking Signaling Data Unit (contains TSBKs) */
#define DUID_LDU2  0xA  /* Logical Link Data Unit 2 */
#define DUID_TDULC 0xF  /* Terminator with Link Control */

/* Maximum dibits in a single frame (LDU is largest at 1728 bits = 864 dibits) */
#define P25_MAX_FRAME_DIBITS 900

/* Frame parser state */
typedef struct {
    /* Sync detection */
    uint64_t sync_reg;       /* Shift register for sync pattern matching */
    int synced;              /* 1 if currently frame-synced */
    int sync_miss_count;     /* Consecutive frames without sync */

    /* Frame accumulation */
    uint8_t frame_dibits[P25_MAX_FRAME_DIBITS];
    int frame_dibit_count;
    int frame_len_expected;

    /* Current frame info from NID */
    uint16_t nac;            /* Network Access Code (12 bits) */
    uint8_t duid;            /* Data Unit ID (4 bits) */

    /* Tuned frequency for reporting */
    double freq_mhz;

    /* RSSI from demodulator */
    float rssi_db;

    /* Statistics */
    uint32_t frames_decoded;
    uint32_t frames_dropped;
} p25_frame_parser_t;

/* Initialize frame parser */
void p25_frame_init(p25_frame_parser_t *parser);

/* Set frequency (MHz) for JSON output */
void p25_frame_set_freq(p25_frame_parser_t *parser, double freq_mhz);

/* Set RSSI (dB) from demodulator for JSON output */
void p25_frame_set_rssi(p25_frame_parser_t *parser, float rssi_db);

/* Feed dibits to the frame parser. Calls p25_event_emit() internally
 * when a complete frame with metadata is decoded.
 * Returns number of events emitted. */
int p25_frame_process(p25_frame_parser_t *parser, const uint8_t *dibits, int count);

#endif /* P25_FRAME_H */
