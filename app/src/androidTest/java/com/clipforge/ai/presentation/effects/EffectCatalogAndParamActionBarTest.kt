package com.clipforge.ai.presentation.effects

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.clipforge.ai.core.effects.EffectCategory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EffectCatalogAndParamActionBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyCatalogRendersEmptyStateAndCategoryTabs() {
        composeRule.setContent {
            EffectCatalogSheet(
                visible = true,
                state = EffectCatalogState.Empty,
                selectedCategory = EffectCategory.TRENDY,
                onDismiss = {},
                onCategorySelected = {},
                onTileClicked = {}
            )
        }

        composeRule.onNodeWithTag(EFFECT_CATALOG_EMPTY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("No effects available yet").assertIsDisplayed()
        composeRule.onNodeWithTag(EFFECT_CATALOG_TABS_TAG).assertIsDisplayed()
        EffectCategory.ordered.forEach { category ->
            composeRule.onNodeWithText(category.displayName).assertIsDisplayed()
        }
    }

    @Test
    fun selectedCategoryVisualStateIsInputDriven() {
        composeRule.setContent {
            EffectCategoryTabs(
                selectedCategory = EffectCategory.BLUR,
                onCategorySelected = {}
            )
        }

        composeRule.onNodeWithTag(EFFECT_CATALOG_SELECTED_TAB_TAG)
            .assertTextContains(EffectCategory.BLUR.displayName)
    }

    @Test
    fun injectedTileAndPremiumBadgeRender() {
        val state = EffectCatalogState(
            categories = listOf(
                EffectCatalogCategoryState(
                    category = EffectCategory.TRENDY,
                    title = "Trendy",
                    tiles = listOf(
                        EffectCatalogTileState(
                            effectId = "brightness",
                            label = "Brightness",
                            category = EffectCategory.TRENDY,
                            isPremium = true
                        )
                    )
                )
            )
        )

        composeRule.setContent {
            EffectCatalogSheet(
                visible = true,
                state = state,
                selectedCategory = EffectCategory.TRENDY,
                onDismiss = {},
                onCategorySelected = {},
                onTileClicked = {}
            )
        }

        composeRule.onNodeWithText("Brightness").assertIsDisplayed()
        composeRule.onAllNodesWithTag(
            testTag = EFFECT_CATALOG_PREMIUM_BADGE_TAG,
            useUnmergedTree = true
        ).assertCountEquals(1)
    }

    @Test
    fun hiddenActionBarRendersNoControls() {
        composeRule.setContent {
            EffectParamActionBar(
                state = EffectActionBarState.Hidden,
                onDelete = {},
                onUndo = {},
                onRedo = {},
                onSliderChanged = { _, _ -> },
                onSliderChangeFinished = {}
            )
        }

        composeRule.onAllNodesWithTag(EFFECT_PARAM_ACTION_BAR_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(EFFECT_PARAM_DELETE_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(EFFECT_PARAM_SLIDER_TAG).assertCountEquals(0)
    }

    @Test
    fun visibleActionBarRendersLabelDeleteAndSliders() {
        composeRule.setContent {
            EffectParamActionBar(
                state = visibleActionBarState(),
                onDelete = {},
                onUndo = {},
                onRedo = {},
                onSliderChanged = { _, _ -> },
                onSliderChangeFinished = {}
            )
        }

        composeRule.onNodeWithTag(EFFECT_PARAM_ACTION_BAR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Brightness").assertIsDisplayed()
        composeRule.onNodeWithTag(EFFECT_PARAM_DELETE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Amount").assertIsDisplayed()
        composeRule.onNodeWithTag(EFFECT_PARAM_SLIDER_TAG).assertIsDisplayed()
    }

    @Test
    fun sliderCallbackIsInvokedWithoutMutatingState() {
        val state = visibleActionBarState()
        val callbackValues = mutableListOf<Pair<String, Float>>()

        composeRule.setContent {
            EffectParamActionBar(
                state = state,
                onDelete = {},
                onUndo = {},
                onRedo = {},
                onSliderChanged = { key, value -> callbackValues += key to value },
                onSliderChangeFinished = {}
            )
        }

        composeRule.onNodeWithTag(EFFECT_PARAM_SLIDER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(0.75f)
            }

        // Assert on an idle, synchronized frame while the Compose rule/activity is still
        // alive. Asserting the action's return value inside the semantics lambda (or reading
        // the callback list straight off the test thread) raced with rule teardown when the
        // full suite ran, surfacing "Activity has been destroyed already".
        composeRule.runOnIdle {
            assertEquals(0.5f, state.sliders.single().value, 0f)
            assertEquals("amount", callbackValues.single().first)
            assertEquals(0.75f, callbackValues.single().second, 0.0001f)
        }
    }

    private fun visibleActionBarState() = EffectActionBarState(
        visible = true,
        selectedEffectId = "effect-1",
        label = "Brightness",
        actions = listOf(EffectAction.Delete),
        sliders = listOf(
            EffectParamSliderState(
                key = "amount",
                label = "Amount",
                min = 0f,
                max = 1f,
                value = 0.5f
            )
        ),
        canUndo = true,
        canRedo = true
    )
}
