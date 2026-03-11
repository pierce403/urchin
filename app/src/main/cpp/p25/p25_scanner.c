/*
 * p25_scanner — P25 Phase 1 metadata decoder for Android
 *
 * Standalone binary that receives IQ samples from an RTL-SDR dongle,
 * demodulates C4FM, parses P25 frames, and outputs metadata as
 * newline-delimited JSON to stdout.
 *
 * Usage:
 *   p25_scanner -f <freq_hz> [-g <gain>] [-d <device_index>]
 *
 * Output format (one JSON object per line):
 *   {"unit_id":"12345","talkgroup":"100","nac":"0x293","rssi":-72.5,"freq":851.0125}
 *
 * This binary does NOT decode voice (no IMBE codec). It captures only
 * control channel metadata for proximity detection and alerting.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include <getopt.h>

#include "c4fm_demod.h"
#include "p25_frame.h"
#include "json_out.h"

#ifdef ENABLE_RTLSDR
#include <rtl-sdr.h>
#endif

/* Default values */
#define DEFAULT_SAMPLE_RATE  48000
#define DEFAULT_GAIN         400    /* 40.0 dB in tenths */
#define IQ_BUFFER_SIZE       (DEFAULT_SAMPLE_RATE / 5)  /* ~200ms chunks */

static volatile int running = 1;

static void signal_handler(int sig) {
    (void)sig;
    running = 0;
}

static void usage(const char *progname) {
    fprintf(stderr,
        "Usage: %s -f <freq_hz> [options]\n"
        "\n"
        "Options:\n"
        "  -f <hz>    Center frequency in Hz (required)\n"
        "  -g <db>    Gain in dB (default: 40, 0 = auto)\n"
        "  -d <idx>   Device index (default: 0)\n"
        "  -s         Stdin mode: read IQ from stdin instead of RTL-SDR\n"
        "  -h         Show this help\n"
        "\n"
        "Output: newline-delimited JSON to stdout\n",
        progname);
}

#ifdef ENABLE_RTLSDR

/* RTL-SDR async callback */
typedef struct {
    c4fm_demod_t demod;
    p25_frame_parser_t parser;
} scanner_state_t;

static void rtlsdr_callback(unsigned char *buf, uint32_t len, void *ctx) {
    scanner_state_t *state = (scanner_state_t *)ctx;

    if (!running) return;

    int num_dibits = c4fm_demod_process(&state->demod, buf, (int)len);
    if (num_dibits > 0) {
        p25_frame_set_rssi(&state->parser, c4fm_demod_rssi(&state->demod));
        p25_frame_process(&state->parser, state->demod.dibit_buf, num_dibits);
    }
}

static int run_rtlsdr(int freq_hz, int gain, int dev_index) {
    rtlsdr_dev_t *dev = NULL;
    int r;

    r = rtlsdr_open(&dev, (uint32_t)dev_index);
    if (r < 0) {
        fprintf(stderr, "Failed to open RTL-SDR device %d: %d\n", dev_index, r);
        return 1;
    }

    rtlsdr_set_sample_rate(dev, DEFAULT_SAMPLE_RATE);
    rtlsdr_set_center_freq(dev, (uint32_t)freq_hz);

    if (gain == 0) {
        rtlsdr_set_tuner_gain_mode(dev, 0); /* Auto gain */
    } else {
        rtlsdr_set_tuner_gain_mode(dev, 1);
        rtlsdr_set_tuner_gain(dev, gain);
    }

    rtlsdr_reset_buffer(dev);

    scanner_state_t state;
    c4fm_demod_init(&state.demod);
    p25_frame_init(&state.parser);
    p25_frame_set_freq(&state.parser, (double)freq_hz / 1e6);

    fprintf(stderr, "p25_scanner: tuned to %d Hz, gain=%d, device=%d\n",
            freq_hz, gain, dev_index);

    r = rtlsdr_read_async(dev, rtlsdr_callback, &state, 0, IQ_BUFFER_SIZE * 2);

    rtlsdr_close(dev);
    return (r < 0) ? 1 : 0;
}

#endif /* ENABLE_RTLSDR */

/*
 * Stdin mode: read raw IQ bytes from stdin.
 * Useful for testing with recorded files or piped data.
 */
static int run_stdin(int freq_hz) {
    c4fm_demod_t demod;
    p25_frame_parser_t parser;

    c4fm_demod_init(&demod);
    p25_frame_init(&parser);
    p25_frame_set_freq(&parser, (double)freq_hz / 1e6);

    fprintf(stderr, "p25_scanner: stdin mode, freq=%d Hz\n", freq_hz);

    uint8_t buf[IQ_BUFFER_SIZE * 2];
    while (running) {
        int n = (int)fread(buf, 1, sizeof(buf), stdin);
        if (n <= 0) break;

        int num_dibits = c4fm_demod_process(&demod, buf, n);
        if (num_dibits > 0) {
            p25_frame_set_rssi(&parser, c4fm_demod_rssi(&demod));
            p25_frame_process(&parser, demod.dibit_buf, num_dibits);
        }
    }

    return 0;
}

int main(int argc, char *argv[]) {
    int freq_hz = 0;
    int gain = DEFAULT_GAIN;
    int dev_index = 0;
    int stdin_mode = 0;
    int opt;

    while ((opt = getopt(argc, argv, "f:g:d:sh")) != -1) {
        switch (opt) {
            case 'f':
                freq_hz = atoi(optarg);
                break;
            case 'g':
                gain = (int)(atof(optarg) * 10.0);
                break;
            case 'd':
                dev_index = atoi(optarg);
                break;
            case 's':
                stdin_mode = 1;
                break;
            case 'h':
            default:
                usage(argv[0]);
                return (opt == 'h') ? 0 : 1;
        }
    }

    if (freq_hz <= 0) {
        fprintf(stderr, "Error: frequency (-f) is required\n");
        usage(argv[0]);
        return 1;
    }

    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    /* Disable stdout buffering for real-time JSON output */
    setvbuf(stdout, NULL, _IONBF, 0);

    if (stdin_mode) {
        return run_stdin(freq_hz);
    }

#ifdef ENABLE_RTLSDR
    return run_rtlsdr(freq_hz, gain, dev_index);
#else
    fprintf(stderr, "Error: RTL-SDR support not compiled in. Use -s for stdin mode.\n");
    return 1;
#endif
}
