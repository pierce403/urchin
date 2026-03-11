package guru.urchin.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import guru.urchin.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @get:Rule
  val activityRule = ActivityScenarioRule(MainActivity::class.java)

  @Test
  fun toolbarShowsAppTitle() {
    onView(withText("Urchin SDR")).check(matches(isDisplayed()))
  }

  @Test
  fun protocolFilterChipsAreDisplayed() {
    onView(withId(R.id.chipAll)).check(matches(isDisplayed()))
    onView(withId(R.id.chipTpms)).check(matches(isDisplayed()))
    onView(withId(R.id.chipPocsag)).check(matches(isDisplayed()))
    onView(withId(R.id.chipAdsb)).check(matches(isDisplayed()))
    onView(withId(R.id.chipP25)).check(matches(isDisplayed()))
  }

  @Test
  fun emptyStateShownWithNoDevices() {
    onView(withId(R.id.emptyState)).check(matches(isDisplayed()))
    onView(withId(R.id.deviceList))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))
  }

  @Test
  fun filterDrawerOpenedByNavigationIcon() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.filterDrawer)).check(matches(isDisplayed()))
  }

  @Test
  fun filterDrawerShowsScanIdleState() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.scanStatus)).check(matches(withText("Idle")))
  }

  @Test
  fun filterDrawerShowsStartButton() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.permissionActionButton)).check(matches(withText("Start")))
  }

  @Test
  fun filterDrawerShowsSourceOptions() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.usbSource)).check(matches(isDisplayed()))
    onView(withId(R.id.networkSource)).check(matches(isDisplayed()))
  }

  @Test
  fun filterDrawerShowsFrequencyOptions() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.freq315)).check(matches(isDisplayed()))
    onView(withId(R.id.freq433)).check(matches(isDisplayed()))
  }

  @Test
  fun filterDrawerShowsProtocolToggles() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.protocolTpms)).check(matches(isDisplayed()))
    onView(withId(R.id.protocolPocsag)).check(matches(isDisplayed()))
    onView(withId(R.id.protocolAdsb)).check(matches(isDisplayed()))
  }

  @Test
  fun filterDrawerShowsFilterAndSortControls() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.filterInput)).check(matches(isDisplayed()))
    onView(withId(R.id.sortSpinner)).check(matches(isDisplayed()))
  }

  @Test
  fun filterDrawerShowsLiveAndStarredCheckboxes() {
    onView(withContentDescription("Open controls")).perform(click())
    onView(withId(R.id.liveOnly)).check(matches(isDisplayed()))
    onView(withId(R.id.starredOnly)).check(matches(isDisplayed()))
    onView(withId(R.id.batteryLowOnly)).check(matches(isDisplayed()))
  }
}
