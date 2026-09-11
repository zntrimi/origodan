package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceCandidateHeightTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun emptyHeightDefaultsUseFactoryValueForBothOrientations() {
        assertEquals(56, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(56, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun mirrorGodanMainLetterSizeDefaultsToTwentySp() {
        assertEquals(20.0f, AppPreference.flick_key_text_size_sp)
    }

    @Test
    fun mirrorGodanCandidateTextDefaultsToSeventeenSp() {
        assertEquals(17.0f, AppPreference.candidate_letter_size)
    }

    @Test
    fun mirrorGodanCandidateTextMigrationUpdatesLegacyDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.candidate_letter_size = 14.0f
        preferences.edit()
            .putBoolean(AppPreference.MIRROR_GODAN_CANDIDATE_TEXT_MIGRATION_KEY, false)
            .commit()

        AppPreference.init(context)

        assertEquals(17.0f, AppPreference.candidate_letter_size)
    }

    @Test
    fun mirrorGodanCandidateTextMigrationUpdatesPreviousForkDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.candidate_letter_size = 16.0f
        preferences.edit()
            .putBoolean(AppPreference.MIRROR_GODAN_CANDIDATE_TEXT_MIGRATION_KEY, false)
            .commit()

        AppPreference.init(context)

        assertEquals(17.0f, AppPreference.candidate_letter_size)
    }

    @Test
    fun emptyHeightDefaultsAreClampedToCandidateHeightRange() {
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 10)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 999)

        assertEquals(30, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(300, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun copyingCurrentHeightsCopiesEmptyHeightDefaultsForBothOrientations() {
        AppPreference.candidate_view_empty_height_dp = 215
        AppPreference.candidate_view_empty_height_dp_landscape = 225

        AppPreference.copyCandidateHeightSettingsToUserDefaults(isLandscape = false)
        AppPreference.copyCandidateHeightSettingsToUserDefaults(isLandscape = true)

        assertEquals(215, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(225, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
    }

    @Test
    fun resettingCurrentHeightsUsesEmptyHeightDefaultsForBothOrientations() {
        AppPreference.candidate_view_empty_height_dp = 215
        AppPreference.candidate_view_empty_height_dp_landscape = 225
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 145)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 155)

        AppPreference.resetCandidateHeightSettingsToUserDefaults(isLandscape = false)
        AppPreference.resetCandidateHeightSettingsToUserDefaults(isLandscape = true)

        assertEquals(145, AppPreference.candidate_view_empty_height_dp)
        assertEquals(155, AppPreference.candidate_view_empty_height_dp_landscape)
    }

    @Test
    fun restoringFactoryDefaultsChangesOnlyEmptyHeightDefault() {
        AppPreference.candidate_view_empty_height_dp = 205
        AppPreference.candidate_view_empty_height_dp_landscape = 215
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = false, heightDp = 175)
        AppPreference.setCandidateDefaultEmptyHeightDp(isLandscape = true, heightDp = 185)

        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = false)
        AppPreference.resetCandidateHeightDefaultsToFactoryDefaults(isLandscape = true)

        assertEquals(56, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = false))
        assertEquals(56, AppPreference.getCandidateDefaultEmptyHeightDp(isLandscape = true))
        assertEquals(205, AppPreference.candidate_view_empty_height_dp)
        assertEquals(215, AppPreference.candidate_view_empty_height_dp_landscape)
    }

    @Test
    fun mirrorGodanGeometryMigrationReplacesOnlyLegacyDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.keyboard_height = 220
        AppPreference.keyboard_height_landscape = 220
        AppPreference.candidate_view_height_dp = 110
        AppPreference.candidate_view_empty_height_dp = 110
        AppPreference.candidate_view_height_dp_landscape = 60
        AppPreference.candidate_view_empty_height_dp_landscape = 110
        preferences.edit()
            .putBoolean(AppPreference.MIRROR_GODAN_GEOMETRY_MIGRATION_KEY, false)
            .commit()

        AppPreference.init(context)

        assertEquals(260, AppPreference.keyboard_height)
        assertEquals(240, AppPreference.keyboard_height_landscape)
        assertEquals(56, AppPreference.candidate_view_height_dp)
        assertEquals(56, AppPreference.candidate_view_empty_height_dp)
        assertEquals(56, AppPreference.candidate_view_height_dp_landscape)
        assertEquals(56, AppPreference.candidate_view_empty_height_dp_landscape)
    }
}
