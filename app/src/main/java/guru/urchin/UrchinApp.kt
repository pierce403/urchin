package guru.urchin

import android.app.Application
import guru.urchin.alerts.DefaultAlertSeeder
import guru.urchin.alerts.DeviceAlertNotifier
import guru.urchin.data.AffinityGroupRepository
import guru.urchin.data.AlertRuleRepository
import guru.urchin.data.AppDatabase
import guru.urchin.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
  val observationRecorder: ObservationRecorder by lazy {
    ObservationRecorder(repository, applicationScope, alertRuleRepository, alertNotifier)
  }
  val sdrController: SdrController by lazy {
    SdrController(this, applicationScope, observationRecorder)
  }

  override fun onCreate() {
    super.onCreate()
    applicationScope.launch {
      DefaultAlertSeeder.seedIfNeeded(this@UrchinApp, alertRuleRepository)
    }
  }
}
