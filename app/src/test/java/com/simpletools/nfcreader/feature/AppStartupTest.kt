package com.simpletools.nfcreader.feature

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.simpletools.nfcreader.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exécuté sous Robolectric : l'appareil simulé n'a PAS de NFC (getDefaultAdapter() == null).
 * Cela vérifie la stabilité sur un téléphone sans NFC, la rotation et la reprise d'arrière-plan.
 * Ce n'est pas une validation matérielle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class AppStartupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun `demarrage sans NFC - accueil affiche, pas de crash`() {
        rule.onNodeWithText("Scanner NFC").assertExists()
        rule.onNodeWithText("Historique").assertExists()
    }

    @Test fun `appareil sans NFC - message explicite sur l'ecran scanner`() {
        rule.onNodeWithText("Scanner NFC").performClick()
        rule.onNodeWithText("NFC indisponible").assertExists()
    }

    @Test fun `rotation - l'ecran scanner survit a la recreation de l'activite`() {
        rule.onNodeWithText("Scanner NFC").performClick()
        rule.onNodeWithText("NFC indisponible").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Scanner NFC").assertExists() // l'activité est recréée sans crash
    }

    @Test fun `mise en arriere-plan puis reprise sans crash`() {
        rule.onNodeWithText("Scanner NFC").performClick()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("NFC indisponible").assertExists()
    }
}
