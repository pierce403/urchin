/*
 * C4FM 4-level FSK Demodulator
 *
 * Demodulates P25 C4FM signals from raw RTL-SDR IQ samples.
 *
 * Pipeline:
 *   IQ samples → FM discriminator → symbol timing recovery → dibit slicer
 *
 * The FM discriminator computes the instantaneous frequency as the phase
 * difference between consecutive samples. The symbol timing recovery uses
 * a simple maximum-energy approach over the samples-per-symbol window.
 * The 4-level slicer maps the recovered amplitude to one of four dibit values.
 */

#include "c4fm_demod.h"
#include <math.h>
#include <string.h>

void c4fm_demod_init(c4fm_demod_t *demod) {
    memset(demod, 0, sizeof(*demod));
    demod->max_level = 1.0f;
    demod->min_level = -1.0f;
    demod->center = 0.0f;
}

void c4fm_demod_reset(c4fm_demod_t *demod) {
    c4fm_demod_init(demod);
}

/*
 * FM discriminator: compute instantaneous frequency from IQ phase delta.
 * Returns normalized frequency deviation in [-1, 1] range.
 */
static float fm_discriminator(float i, float q, float prev_i, float prev_q) {
    /* Conjugate multiply: (i + jq) * conj(prev_i + j*prev_q) */
    float real = i * prev_i + q * prev_q;
    float imag = q * prev_i - i * prev_q;
    return atan2f(imag, real) / (float)M_PI;
}

/*
 * 4-level slicer: map FM discriminator output to P25 dibit.
 * Threshold levels divide the [-1, 1] range into 4 decision regions.
 */
static uint8_t slice_symbol(float sample, float center, float span) {
    float normalized;
    if (span < 0.001f) {
        normalized = 0.0f;
    } else {
        normalized = (sample - center) / (span * 0.5f);
    }

    if (normalized > 0.5f)       return DIBIT_P3;
    else if (normalized > 0.0f)  return DIBIT_P1;
    else if (normalized > -0.5f) return DIBIT_M1;
    else                         return DIBIT_M3;
}

int c4fm_demod_process(c4fm_demod_t *demod, const uint8_t *iq_data, int iq_len) {
    demod->dibit_count = 0;
    int num_samples = iq_len / 2;

    for (int n = 0; n < num_samples; n++) {
        /* Convert uint8 IQ to float [-1, 1] */
        float i_f = (iq_data[n * 2]     - 127.5f) / 127.5f;
        float q_f = (iq_data[n * 2 + 1] - 127.5f) / 127.5f;

        /* FM discriminator */
        float fm = fm_discriminator(i_f, q_f, demod->prev_i, demod->prev_q);
        demod->prev_i = i_f;
        demod->prev_q = q_f;

        /* Track signal level for RSSI estimation */
        float mag = sqrtf(i_f * i_f + q_f * q_f);
        demod->signal_level = demod->signal_level * 0.999f + mag * 0.001f;

        /* Accumulate samples for symbol timing */
        demod->sample_buf[demod->sample_idx] = fm;
        demod->sample_idx++;

        if (demod->sample_idx >= C4FM_SAMPLES_PER_SYM) {
            /* Find optimal sample point (maximum absolute value) */
            float best_val = 0.0f;
            int best_idx = C4FM_SAMPLES_PER_SYM / 2;
            for (int k = 0; k < C4FM_SAMPLES_PER_SYM; k++) {
                float abs_val = fabsf(demod->sample_buf[k]);
                if (abs_val > fabsf(best_val)) {
                    best_val = demod->sample_buf[k];
                    best_idx = k;
                }
            }

            float sample = demod->sample_buf[best_idx];

            /* Track min/max for adaptive slicing with exponential decay */
            if (sample > demod->max_level) {
                demod->max_level = sample;
            } else {
                demod->max_level = demod->max_level * 0.9999f + sample * 0.0001f;
            }
            if (sample < demod->min_level) {
                demod->min_level = sample;
            } else {
                demod->min_level = demod->min_level * 0.9999f + sample * 0.0001f;
            }

            demod->center = (demod->max_level + demod->min_level) * 0.5f;
            float span = demod->max_level - demod->min_level;

            /* Slice to dibit */
            uint8_t dibit = slice_symbol(sample, demod->center, span);

            if (demod->dibit_count < (int)(sizeof(demod->dibit_buf))) {
                demod->dibit_buf[demod->dibit_count++] = dibit;
            }

            demod->sample_idx = 0;
            demod->total_symbols++;
        }
    }

    return demod->dibit_count;
}

float c4fm_demod_rssi(const c4fm_demod_t *demod) {
    if (demod->signal_level <= 0.0f) return -120.0f;
    return 20.0f * log10f(demod->signal_level);
}
