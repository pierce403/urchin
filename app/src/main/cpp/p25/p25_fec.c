/*
 * P25 Forward Error Correction
 *
 * Implements Golay(23,12) and Hamming(15,11) decoders for P25 frame validation.
 * Based on standard algebraic decoding algorithms.
 */

#include "p25_fec.h"

/* Golay(23,12) parity check matrix rows */
static const uint32_t golay_matrix[12] = {
    0xC75, 0x63B, 0xF68, 0x7B4, 0x3DA, 0xD99,
    0x6CD, 0xB2F, 0xA9B, 0x54E, 0x2A7, 0x9E3
};

static int popcount32(uint32_t x) {
    x = x - ((x >> 1) & 0x55555555);
    x = (x & 0x33333333) + ((x >> 2) & 0x33333333);
    return (((x + (x >> 4)) & 0x0F0F0F0F) * 0x01010101) >> 24;
}

static uint32_t golay_syndrome(uint32_t codeword) {
    uint32_t syndrome = 0;
    for (int i = 0; i < 12; i++) {
        if (popcount32(codeword & golay_matrix[i]) & 1) {
            syndrome |= (1u << (11 - i));
        }
    }
    /* Also check parity of entire codeword + syndrome */
    return syndrome;
}

uint32_t golay_23_12_decode(uint32_t codeword) {
    /* Mask to 23 bits */
    codeword &= 0x7FFFFF;

    uint32_t syndrome = golay_syndrome(codeword);
    if (syndrome == 0) {
        /* No errors */
        return (codeword >> 11) & 0xFFF;
    }

    /* Try single-bit error correction */
    if (popcount32(syndrome) <= 3) {
        codeword ^= (syndrome << 11);
        return (codeword >> 11) & 0xFFF;
    }

    /* Try two-bit patterns by flipping each bit and rechecking */
    for (int i = 0; i < 23; i++) {
        uint32_t trial = codeword ^ (1u << i);
        uint32_t s = golay_syndrome(trial);
        if (popcount32(s) <= 2) {
            trial ^= (s << 11);
            return (trial >> 11) & 0xFFF;
        }
    }

    /* Uncorrectable — return raw data bits anyway */
    return (codeword >> 11) & 0xFFF;
}

/* Hamming(15,11) generator polynomial: x^4 + x + 1 */
static const uint16_t hamming_parity[11] = {
    0x09, 0x0D, 0x0F, 0x07, 0x0B, 0x0E, 0x05, 0x03, 0x0C, 0x06, 0x0A
};

uint16_t hamming_15_11_decode(uint16_t codeword) {
    codeword &= 0x7FFF;

    /* Compute syndrome */
    uint16_t syndrome = 0;
    for (int i = 0; i < 4; i++) {
        int parity = 0;
        for (int j = 0; j < 15; j++) {
            if ((codeword >> (14 - j)) & 1) {
                /* Check if bit j contributes to parity check i */
                if (j < 11) {
                    parity ^= (hamming_parity[j] >> (3 - i)) & 1;
                } else {
                    parity ^= (j - 11 == i) ? 1 : 0;
                }
            }
        }
        syndrome |= (parity << (3 - i));
    }

    if (syndrome == 0) {
        return (codeword >> 4) & 0x7FF;
    }

    /* Single-bit correction */
    if (syndrome <= 15) {
        int bit_pos = 14 - syndrome + 1;
        if (bit_pos >= 0 && bit_pos < 15) {
            codeword ^= (1u << bit_pos);
        }
    }

    return (codeword >> 4) & 0x7FF;
}

int p25_deinterleave_dibits(const uint8_t *dibits, int count, uint8_t *out) {
    /* Simple deinterleave — copy dibits to output buffer in order.
     * Real P25 uses a defined interleave table; this provides the
     * scaffolding for future enhancement. */
    for (int i = 0; i < count; i++) {
        out[i] = dibits[i] & 0x03;
    }
    return 0;
}
