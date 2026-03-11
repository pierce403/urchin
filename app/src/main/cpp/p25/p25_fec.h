/*
 * P25 Forward Error Correction
 * Golay(23,12), Hamming(15,11), and trellis decoding for P25 frame validation.
 */

#ifndef P25_FEC_H
#define P25_FEC_H

#include <stdint.h>

/* Golay(23,12) — used in TSBK, HDU, LDU link control words */
uint32_t golay_23_12_decode(uint32_t codeword);

/* Hamming(15,11) — used in various P25 fields */
uint16_t hamming_15_11_decode(uint16_t codeword);

/* Extract data bits from raw dibit stream with deinterleaving.
 * Returns number of corrected errors, or -1 if uncorrectable. */
int p25_deinterleave_dibits(const uint8_t *dibits, int count, uint8_t *out);

#endif /* P25_FEC_H */
