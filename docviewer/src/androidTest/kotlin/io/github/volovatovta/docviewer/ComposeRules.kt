package io.github.volovatovta.docviewer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.test.StandardTestDispatcher

// By default the test recomposer is unconfined: a viewer coroutine resuming from a background thread would then
// compose on that thread. A standard dispatcher keeps composition on the main thread, as in an app.
@OptIn(ExperimentalTestApi::class)
fun viewerComposeRule(): ComposeContentTestRule = createComposeRule(StandardTestDispatcher())

@OptIn(ExperimentalTestApi::class)
inline fun <reified A : ComponentActivity> viewerActivityRule(): AndroidComposeTestRule<ActivityScenarioRule<A>, A> =
    createAndroidComposeRule<A>(StandardTestDispatcher())
