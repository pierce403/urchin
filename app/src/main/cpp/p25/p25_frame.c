/*
 * P25 Phase 1 Frame Parser
 *
 * Synchronizes on P25 frame boundaries, decodes the NID (Network ID)
 * from each frame, classifies frame type by DUID, and extracts metadata
 * from TSBK, HDU, TDU, and LDU link control words.
 *
 * Reference: TIA-102.BAAA-A (P25 Common Air Interface)
 */

#include "p25_frame.h"
#include "p25_fec.h"
#include <string.h>
#include <stdio.h>

/* P25 frame sync pattern as dibits: 01 01 01 11 01 01 11 11 01 01 11 11 11 11 01 11 11 11 11 11 11 11 11 11
 * Hex: 0x5575F5FF77FF (48 bits = 24 dibits) */
static const uint64_t P25_SYNC_PATTERN = 0x5575F5FF77FFULL;
static const uint64_t P25_SYNC_MASK    = 0xFFFFFFFFFFFFULL; /* 48-bit mask */

/* Maximum bit errors allowed in sync pattern */
#define SYNC_THRESHOLD 4

/* Frame lengths in dibits (after sync + NID) */
#define HDU_FRAME_LEN   328
#define TDU_FRAME_LEN   0    /* No payload after NID */
#define TDULC_FRAME_LEN 120
#define LDU1_FRAME_LEN  864
#define LDU2_FRAME_LEN  864
#define TSDU_FRAME_LEN  348

static int popcount64(uint64_t x) {
    x = x - ((x >> 1) & 0x5555555555555555ULL);
    x = (x & 0x3333333333333333ULL) + ((x >> 2) & 0x3333333333333333ULL);
    return (int)((((x + (x >> 4)) & 0x0F0F0F0F0F0F0F0FULL) * 0x0101010101010101ULL) >> 56);
}

static int check_sync(uint64_t reg) {
    uint64_t diff = (reg ^ P25_SYNC_PATTERN) & P25_SYNC_MASK;
    return popcount64(diff) <= SYNC_THRESHOLD;
}

void p25_frame_init(p25_frame_parser_t *parser) {
    memset(parser, 0, sizeof(*parser));
    parser->rssi_db = -120.0f;
}

void p25_frame_set_freq(p25_frame_parser_t *parser, double freq_mhz) {
    parser->freq_mhz = freq_mhz;
}

void p25_frame_set_rssi(p25_frame_parser_t *parser, float rssi_db) {
    parser->rssi_db = rssi_db;
}

/*
 * Decode NID field (64 bits = 32 dibits after sync).
 * Contains 12-bit NAC + 4-bit DUID + 48-bit BCH parity.
 */
static void decode_nid(p25_frame_parser_t *parser, const uint8_t *dibits) {
    /* Reconstruct 64-bit NID from dibits */
    uint64_t nid = 0;
    for (int i = 0; i < 32; i++) {
        nid = (nid << 2) | (dibits[i] & 0x03);
    }

    /* NAC is in bits [63:52] and DUID in bits [51:48] */
    parser->nac = (uint16_t)((nid >> 52) & 0xFFF);
    parser->duid = (uint8_t)((nid >> 48) & 0x0F);
}

/*
 * Determine expected frame length from DUID.
 */
static int frame_length_for_duid(uint8_t duid) {
    switch (duid) {
        case DUID_HDU:   return HDU_FRAME_LEN;
        case DUID_TDU:   return TDU_FRAME_LEN;
        case DUID_TDULC: return TDULC_FRAME_LEN;
        case DUID_LDU1:  return LDU1_FRAME_LEN;
        case DUID_LDU2:  return LDU2_FRAME_LEN;
        case DUID_TSDU:  return TSDU_FRAME_LEN;
        default:         return 0;
    }
}

/*
 * Decode TSBK (Trunking Signaling Block).
 * Contains opcode + source/destination unit IDs + talk group.
 */
static int decode_tsbk(p25_frame_parser_t *parser, const uint8_t *dibits, int count) {
    if (count < 96) return 0;  /* Need at least 192 bits */

    /* Reconstruct data bytes from dibits */
    uint8_t data[24];
    memset(data, 0, sizeof(data));
    for (int i = 0; i < 96 && i < count; i++) {
        int byte_idx = i / 4;
        int bit_pos = 6 - (i % 4) * 2;
        if (byte_idx < 24) {
            data[byte_idx] |= (dibits[i] & 0x03) << bit_pos;
        }
    }

    /* TSBK opcode is in first byte, bits [7:2] (6 bits) */
    uint8_t opcode = (data[0] >> 2) & 0x3F;

    p25_event_t evt;
    p25_event_init(&evt);
    snprintf(evt.nac, sizeof(evt.nac), "0x%03X", parser->nac);

    /* Extract fields based on opcode */
    switch (opcode) {
        case 0x02: /* Group Voice Channel Grant */
        case 0x00: /* Group Voice Channel Grant Update */
        {
            /* Source unit ID at bytes 9-11, talk group at bytes 6-7 */
            uint32_t src = ((uint32_t)data[9] << 16) | ((uint32_t)data[10] << 8) | data[11];
            uint16_t tg = ((uint16_t)data[6] << 8) | data[7];
            snprintf(evt.unit_id, sizeof(evt.unit_id), "%u", src);
            snprintf(evt.talkgroup, sizeof(evt.talkgroup), "%u", tg);
            break;
        }
        case 0x28: /* Unit Registration Response */
        case 0x2C: /* Unit Registration Command */
        {
            uint32_t src = ((uint32_t)data[9] << 16) | ((uint32_t)data[10] << 8) | data[11];
            snprintf(evt.unit_id, sizeof(evt.unit_id), "%u", src);
            break;
        }
        case 0x2A: /* System ID Broadcast */
        {
            uint16_t wacn_hi = ((uint16_t)data[2] << 8) | data[3];
            uint8_t wacn_lo = data[4] >> 4;
            uint32_t wacn = ((uint32_t)wacn_hi << 4) | wacn_lo;
            uint16_t sysid = ((uint16_t)(data[4] & 0x0F) << 8) | data[5];
            snprintf(evt.wacn, sizeof(evt.wacn), "0x%05X", wacn);
            snprintf(evt.system_id, sizeof(evt.system_id), "0x%03X", sysid);
            /* System broadcasts don't have a specific unit; skip */
            return 0;
        }
        default:
            /* Unknown opcode — no useful metadata */
            return 0;
    }

    if (parser->rssi_db > -120.0f) {
        evt.rssi = (double)parser->rssi_db;
        evt.has_rssi = 1;
    }
    if (parser->freq_mhz > 0.0) {
        evt.freq = parser->freq_mhz;
        evt.has_freq = 1;
    }

    return (p25_event_emit(&evt) == 0) ? 1 : 0;
}

/*
 * Decode HDU (Header Data Unit).
 * Contains source unit ID at the start of a voice call.
 */
static int decode_hdu(p25_frame_parser_t *parser, const uint8_t *dibits, int count) {
    if (count < 162) return 0;

    /* HDU contains: Message Indicator (72b) + MFID (8b) + Algo ID (8b) +
     * Key ID (16b) + Talk Group (16b) after Golay encoding.
     * Source unit ID is in the Link Control from preceding TSBK grant. */

    uint8_t data[40];
    memset(data, 0, sizeof(data));
    for (int i = 0; i < count && i / 4 < 40; i++) {
        int byte_idx = i / 4;
        int bit_pos = 6 - (i % 4) * 2;
        data[byte_idx] |= (dibits[i] & 0x03) << bit_pos;
    }

    /* Talk group is at bytes 14-15 in the decoded HDU payload */
    uint16_t tg = ((uint16_t)data[14] << 8) | data[15];
    if (tg == 0) return 0;

    p25_event_t evt;
    p25_event_init(&evt);
    snprintf(evt.nac, sizeof(evt.nac), "0x%03X", parser->nac);
    snprintf(evt.talkgroup, sizeof(evt.talkgroup), "%u", tg);
    /* HDU doesn't directly contain unit_id; use talkgroup as identifier */
    snprintf(evt.unit_id, sizeof(evt.unit_id), "tg-%u", tg);

    if (parser->rssi_db > -120.0f) {
        evt.rssi = (double)parser->rssi_db;
        evt.has_rssi = 1;
    }
    if (parser->freq_mhz > 0.0) {
        evt.freq = parser->freq_mhz;
        evt.has_freq = 1;
    }

    return (p25_event_emit(&evt) == 0) ? 1 : 0;
}

/*
 * Decode TDU with Link Control.
 * Contains talk group and unit info from the terminated voice call.
 */
static int decode_tdulc(p25_frame_parser_t *parser, const uint8_t *dibits, int count) {
    if (count < 60) return 0;

    uint8_t data[15];
    memset(data, 0, sizeof(data));
    for (int i = 0; i < count && i / 4 < 15; i++) {
        int byte_idx = i / 4;
        int bit_pos = 6 - (i % 4) * 2;
        data[byte_idx] |= (dibits[i] & 0x03) << bit_pos;
    }

    /* Link control: opcode (8b) + MFID (8b) + talk group (16b) + source (24b) */
    uint8_t lc_opcode = data[0];
    (void)lc_opcode;

    uint16_t tg = ((uint16_t)data[2] << 8) | data[3];
    uint32_t src = ((uint32_t)data[4] << 16) | ((uint32_t)data[5] << 8) | data[6];

    if (src == 0 && tg == 0) return 0;

    p25_event_t evt;
    p25_event_init(&evt);
    snprintf(evt.nac, sizeof(evt.nac), "0x%03X", parser->nac);
    if (src != 0) {
        snprintf(evt.unit_id, sizeof(evt.unit_id), "%u", src);
    }
    if (tg != 0) {
        snprintf(evt.talkgroup, sizeof(evt.talkgroup), "%u", tg);
    }
    /* Need unit_id for emission */
    if (evt.unit_id[0] == '\0' && tg != 0) {
        snprintf(evt.unit_id, sizeof(evt.unit_id), "tg-%u", tg);
    }

    if (parser->rssi_db > -120.0f) {
        evt.rssi = (double)parser->rssi_db;
        evt.has_rssi = 1;
    }
    if (parser->freq_mhz > 0.0) {
        evt.freq = parser->freq_mhz;
        evt.has_freq = 1;
    }

    return (p25_event_emit(&evt) == 0) ? 1 : 0;
}

/*
 * Extract embedded link control from LDU1/LDU2 frames.
 * Link control words are spread across the frame in designated positions.
 */
static int decode_ldu_link_control(p25_frame_parser_t *parser, const uint8_t *dibits, int count) {
    if (count < 400) return 0;

    /* LDU link control is embedded across voice frame chunks.
     * Simplified extraction: bytes at known offsets contain LC data.
     * Full implementation would deinterleave the LC fragments. */

    /* Reconstruct partial LC from frame offsets (simplified) */
    uint8_t lc_data[9];
    memset(lc_data, 0, sizeof(lc_data));

    /* LC fragments are at dibit positions 114-131, 266-283, 418-435 in LDU1
     * Each fragment is 18 dibits = 36 bits = 4.5 bytes */
    static const int lc_offsets[] = { 114, 266, 418 };
    int byte_pos = 0;
    for (int frag = 0; frag < 3 && byte_pos < 9; frag++) {
        int offset = lc_offsets[frag];
        if (offset + 18 > count) break;
        for (int i = 0; i < 18 && byte_pos < 9; i++) {
            int global_bit = byte_pos * 8 + (i % 4) * 2;
            int dest_byte = global_bit / 8;
            int dest_bit = 6 - (global_bit % 8);
            if (dest_byte < 9 && dest_bit >= 0) {
                lc_data[dest_byte] |= (dibits[offset + i] & 0x03) << dest_bit;
            }
            if (i % 4 == 3) byte_pos++;
        }
    }

    /* LC format: opcode (8b) + MFID (8b) + talkgroup (16b) + source (24b) */
    uint16_t tg = ((uint16_t)lc_data[2] << 8) | lc_data[3];
    uint32_t src = ((uint32_t)lc_data[4] << 16) | ((uint32_t)lc_data[5] << 8) | lc_data[6];

    if (src == 0 && tg == 0) return 0;

    p25_event_t evt;
    p25_event_init(&evt);
    snprintf(evt.nac, sizeof(evt.nac), "0x%03X", parser->nac);
    if (src != 0) {
        snprintf(evt.unit_id, sizeof(evt.unit_id), "%u", src);
    } else {
        snprintf(evt.unit_id, sizeof(evt.unit_id), "tg-%u", tg);
    }
    if (tg != 0) {
        snprintf(evt.talkgroup, sizeof(evt.talkgroup), "%u", tg);
    }

    if (parser->rssi_db > -120.0f) {
        evt.rssi = (double)parser->rssi_db;
        evt.has_rssi = 1;
    }
    if (parser->freq_mhz > 0.0) {
        evt.freq = parser->freq_mhz;
        evt.has_freq = 1;
    }

    return (p25_event_emit(&evt) == 0) ? 1 : 0;
}

/*
 * Dispatch decoded frame to the appropriate handler based on DUID.
 */
static int dispatch_frame(p25_frame_parser_t *parser) {
    int events = 0;
    const uint8_t *payload = parser->frame_dibits;
    int len = parser->frame_dibit_count;

    switch (parser->duid) {
        case DUID_TSDU:
            events = decode_tsbk(parser, payload, len);
            break;
        case DUID_HDU:
            events = decode_hdu(parser, payload, len);
            break;
        case DUID_TDU:
            /* Simple TDU has no payload */
            break;
        case DUID_TDULC:
            events = decode_tdulc(parser, payload, len);
            break;
        case DUID_LDU1:
        case DUID_LDU2:
            events = decode_ldu_link_control(parser, payload, len);
            break;
        default:
            break;
    }

    if (events > 0) {
        parser->frames_decoded++;
    }
    return events;
}

int p25_frame_process(p25_frame_parser_t *parser, const uint8_t *dibits, int count) {
    int total_events = 0;

    for (int i = 0; i < count; i++) {
        uint8_t dibit = dibits[i] & 0x03;

        /* Shift dibit into sync detection register (2 bits per dibit) */
        parser->sync_reg = ((parser->sync_reg << 2) | dibit) & P25_SYNC_MASK;

        if (!parser->synced) {
            /* Searching for sync pattern */
            if (check_sync(parser->sync_reg)) {
                parser->synced = 1;
                parser->sync_miss_count = 0;
                parser->frame_dibit_count = 0;
                /* Next 32 dibits are NID */
                parser->frame_len_expected = 32; /* NID first */
            }
        } else {
            /* Accumulating frame data */
            if (parser->frame_dibit_count < P25_MAX_FRAME_DIBITS) {
                parser->frame_dibits[parser->frame_dibit_count++] = dibit;
            }

            /* After NID (32 dibits), determine frame length */
            if (parser->frame_dibit_count == 32) {
                decode_nid(parser, parser->frame_dibits);
                int payload_len = frame_length_for_duid(parser->duid);
                parser->frame_len_expected = 32 + payload_len;
            }

            /* Frame complete */
            if (parser->frame_dibit_count >= parser->frame_len_expected &&
                parser->frame_len_expected > 0) {
                total_events += dispatch_frame(parser);

                /* Reset for next frame */
                parser->synced = 0;
                parser->frame_dibit_count = 0;
            }
        }
    }

    return total_events;
}
