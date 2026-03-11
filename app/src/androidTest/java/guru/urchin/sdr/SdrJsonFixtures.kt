package guru.urchin.sdr

/**
 * Factory methods for [SdrReading] test data, matching the profiles from
 * scripts/sdr-simulator.py for consistency.
 */
object SdrJsonFixtures {

  val RTL_SDR_PROFILE = SdrHardwareProfile(
    label = "RTL-SDR",
    usbVendorId = 0x0BDA,
    usbProductId = 0x2838,
    rtl433DeviceArg = "driver=rtlsdr"
  )

  val HACKRF_PROFILE = SdrHardwareProfile(
    label = "HackRF One",
    usbVendorId = 0x1D50,
    usbProductId = 0x6089,
    rtl433DeviceArg = "driver=hackrf"
  )

  fun singleRtlSdr() = listOf(SdrDeviceHandle(id = 0, profile = RTL_SDR_PROFILE))

  fun dualRtlSdr() = listOf(
    SdrDeviceHandle(id = 0, profile = RTL_SDR_PROFILE),
    SdrDeviceHandle(id = 1, profile = RTL_SDR_PROFILE)
  )

  fun tpmsReading(
    model: String = "PMV-107J",
    sensorId: String = "0x00ABCDEF",
    pressureKpa: Double? = 220.5,
    temperatureC: Double? = 28.0,
    batteryOk: Boolean? = true,
    status: Int? = 0,
    rssi: Double? = -12.3,
    snr: Double? = 15.0,
    frequencyMhz: Double? = 433.92
  ) = SdrReading.Tpms(
    model = model,
    sensorId = sensorId,
    pressureKpa = pressureKpa,
    temperatureC = temperatureC,
    batteryOk = batteryOk,
    status = status,
    rssi = rssi,
    snr = snr,
    frequencyMhz = frequencyMhz,
    rawJson = """{"model":"$model","id":"$sensorId","type":"TPMS","pressure_kPa":$pressureKpa}"""
  )

  fun pocsagReading(
    address: String = "1234567",
    functionCode: Int = 1,
    message: String? = "RESPOND TO 123 MAIN ST APT 4 - MEDICAL EMERGENCY",
    model: String = "Flex",
    rssi: Double? = -18.5
  ) = SdrReading.Pocsag(
    address = address,
    functionCode = functionCode,
    message = message,
    model = model,
    rssi = rssi,
    snr = null,
    frequencyMhz = 929.6125,
    rawJson = """{"model":"$model","address":"$address","function":$functionCode}"""
  )

  fun adsbReading(
    icao: String = "A00001",
    callsign: String? = "AAL123",
    altitude: Int? = 35000,
    speed: Double? = 450.0,
    heading: Double? = 270.0,
    lat: Double? = 40.6413,
    lon: Double? = -73.7781,
    squawk: String? = null,
    rssi: Double? = -15.0
  ) = SdrReading.Adsb(
    icao = icao,
    callsign = callsign,
    altitude = altitude,
    speed = speed,
    heading = heading,
    lat = lat,
    lon = lon,
    squawk = squawk,
    rssi = rssi,
    snr = null,
    frequencyMhz = 1090.0,
    rawJson = """{"hex":"$icao","type":"adsb_icao","flight":"$callsign"}"""
  )

  fun p25Reading(
    unitId: String = "12345",
    nac: String? = "0x2A1",
    wacn: String? = null,
    systemId: String? = "0x001",
    talkGroupId: String? = "1001",
    rssi: Double? = -20.0
  ) = SdrReading.P25(
    unitId = unitId,
    nac = nac,
    wacn = wacn,
    systemId = systemId,
    talkGroupId = talkGroupId,
    rssi = rssi,
    snr = null,
    frequencyMhz = 851.0,
    rawJson = """{"unit_id":"$unitId","nac":"$nac","talkgroup":"$talkGroupId"}"""
  )
}
