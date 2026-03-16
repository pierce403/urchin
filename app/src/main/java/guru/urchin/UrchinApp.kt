package guru.urchin

import android.app.Application
import guru.urchin.alerts.DefaultAlertSeeder
import guru.urchin.alerts.DeviceAlertNotifier
import guru.urchin.analysis.AnomalyDetector
import guru.urchin.analysis.CorrelationEngine
import guru.urchin.data.AffinityGroupRepository
import guru.urchin.data.AlertRuleRepository
import guru.urchin.data.AppDatabase
import guru.urchin.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import guru.urchin.scan.LocationProvider
import guru.urchin.scan.ObservationRecorder
import guru.urchin.sdr.SdrController

class UrchinApp : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val database: AppDatabase by lazy { AppDatabase.build(this) }
  val repository: DeviceRepository by lazy {
    DeviceRepository(database, database.deviceDao(), database.sightingDao())
  }
  val alertRuleRepository: AlertRuleRepository by lazy {
    AlertRuleRepository(database.alertRuleDao())
  }
  val affinityGroupRepository: AffinityGroupRepository by lazy {
    AffinityGroupRepository(database.affinityGroupDao())
  }
  val alertNotifier: DeviceAlertNotifier by lazy {
    DeviceAlertNotifier(this)
  }
  val locationProvider: LocationProvider by lazy {
    LocationProvider(this)
  }
  val observationRecorder: ObservationRecorder by lazy {
    ObservationRecorder(repository, applicationScope, alertRuleRepository, alertNotifier, locationProvider)
  }
  val sdrController: SdrController by lazy {
    SdrController(this, applicationScope, observationRecorder)
  }
  val correlationEngine: CorrelationEngine by lazy {
    CorrelationEngine(database.sightingDao(), database.correlationDao())
  }
  val anomalyDetector: AnomalyDetector by lazy {
    AnomalyDetector(database.deviceDao(), database.sightingDao())
  }

  override fun onCreate() {
    super.onCreate()
    locationProvider.start()
    applicationScope.launch {
      DefaultAlertSeeder.seedIfNeeded(this@UrchinApp, alertRuleRepository)
    }
    applicationScope.launch {
      while (true) {
        delay(CORRELATION_INTERVAL_MS)
        try {
          correlationEngine.runCorrelation()
          anomalyDetector.runDetection()
        } catch (e: Exception) {
          guru.urchin.util.DebugLog.log("Analysis error: ${e.message}")
        }
      }
    }
  }

  companion object {
    private const val CORRELATION_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
  }
}
