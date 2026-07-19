package com.tenanatc.cocktailmaker

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke tests for the fully offline path: no camera, no network,
 * no API key — pick ingredients by hand and get recipes.
 */
@RunWith(AndroidJUnit4::class)
class OfflineFlowSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pickIngredientsByHand_suggestsGinAndTonic() {
        compose.onNodeWithText("Pick ingredients by hand").performClick()

        compose.onNodeWithText("Gin").performScrollTo().performClick()
        compose.onNodeWithText("Tonic Water").performScrollTo().performClick()
        compose.onNodeWithText("Find cocktails").performScrollTo().performClick()

        compose.onNodeWithText("Gin & Tonic").assertExists()
    }

    @Test
    fun browseRecipes_searchFindsMargarita() {
        compose.onNodeWithText("Browse all recipes (offline)").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("margarita")

        compose.onNodeWithText("Margarita").assertExists()
        compose.onNodeWithText("Tommy's Margarita").assertExists()
    }

    @Test
    fun recipeDetail_showsIngredientsAndMethod() {
        compose.onNodeWithText("Browse all recipes (offline)").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("negroni")
        compose.onNodeWithText("Negroni").performClick()

        compose.onNodeWithText("Ingredients").assertExists()
        compose.onNodeWithText("Method").assertExists()
        compose.onNodeWithText("Serve in: Rocks glass").assertExists()
    }
}
