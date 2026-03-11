/*
 * C4FM 4-level FSK Demodulator
 *
 * Recovers P25 dibit symbols (00, 01, 10, 11) from raw IQ samples.
 * P25 Phase 1 uses C4FM at 4800 symbols/sec (9600 baud) with
 * deviation levels at ±1800 Hz and ±600 Hz.
 */

#ifndef C4FM_DEMOD_H
#define C4FM_DEMOD_H

#include <stdint.h>

/* Sample rate after decimation — 48 kHz is standard for P25 */
#define C4FM_SAMPLE_RATE     48000
#define C4FM_SYMBOL_RATE     4800
#define C4FM_SAMPLES_PER_SYM (C4FM_SAMPLE_RATE / C4FM_SYMBOL_RATE)

/* Dibit values (P25 symbol mapping) */
#define DIBIT_P3  0  /* +3 deviation: 01 */
#define DIBIT_P1  1  /* +1 deviation: 00 */
#define DIBIT_M1  2  /* -1 deviation: 10 */
#define DIBIT_M3  3  /* -3 deviation: 11 */

typedef struct {
    /* FM discriminator state */
    float prev_i;
    float prev_q;

    /* Symbol timing recovery */
    float sample_buf[C4FM_SAMPLES_PER_SYM * 3];
    int sample_idx;
    int sym_count;

    /* AGC / level tracking */
    float max_level;
    float min_level;
    float center;

    /* Output buffer */
    uint8_t dibit_buf[256];
    int dibit_count;

    /* Signal quality */
    float signal_level;
    int total_symbols;
} c4fm_demod_t;

/* Initialize demodulator state */
void c4fm_demod_init(c4fm_demod_t *demod);

/* Process a block of interleaved IQ samples (int8 from RTL-SDR).
 * Returns number of dibits written to demod->dibit_buf. */
int c4fm_demod_process(c4fm_demod_t *demod, const uint8_t *iq_data, int iq_len);

/* Get current estimated signal strength in dB */
float c4fm_demod_rssi(const c4fm_demod_t *demod);

/* Reset demodulator state (e.g., on frequency change) */
void c4fm_demod_reset(c4fm_demod_t *demod);

#endif /* C4FM_DEMOD_H */
