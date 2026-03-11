#ifndef GURU_URCHIN_RTL433_ANDROID_DISABLE_RTLTCP_H_
#define GURU_URCHIN_RTL433_ANDROID_DISABLE_RTLTCP_H_

#if defined(__ANDROID__)
/*
 * Android's bionic libc does not provide pthread_cancel(). rtl_433's rtl_tcp
 * output references it, but Urchin only uses rtl_433's stdout/json mode.
 * Stub the call so the bundled executable can be built for Android.
 */
#include <pthread.h>

static inline int urchin_android_pthread_cancel(pthread_t thread)
{
  (void)thread;
  return 0;
}

#define pthread_cancel(thread) urchin_android_pthread_cancel(thread)
#endif

#endif /* GURU_URCHIN_RTL433_ANDROID_DISABLE_RTLTCP_H_ */
