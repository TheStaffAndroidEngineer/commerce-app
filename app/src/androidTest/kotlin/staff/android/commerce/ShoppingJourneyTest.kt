package staff.android.commerce

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the production Hilt graph and real local file adapter, not fake composable state.
 * Starting contract: clear only staff.android.commerce before the connected test invocation:
 * adb shell pm clear staff.android.commerce
 * This single journey deliberately proves the empty starting state before making accepted edits.
 * A separate host journey owns force-stop/relaunch persistence; Activity recreation is not that test.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingJourneyTest {
    @get:org.junit.Rule(order=0) val nearby = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun shopping_preserves_accepted_quantity_through_draft_rejection_and_demo_checkout() {
        waitForTag("nav-cart")
        compose.onNodeWithTag("nav-cart").performClick()
        waitForTag("cart-browse")
        compose.onNodeWithText("A little room for something good.").assertIsDisplayed()
        compose.onNodeWithTag("cart-quantity-tea").assertDoesNotExist()

        compose.onNodeWithTag("nav-search").performClick()
        waitForTag("search-field")
        compose.onNodeWithTag("search-field").performTextReplacement("Tea")
        compose.onNodeWithTag("search-field").performImeAction()
        waitForTag("product-tea")
        // Target the card's navigation action; its image-error overlay owns a different Retry action.
        compose.onNodeWithTag("product-tea").performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitForTag("detail-add-tea")
        compose.onNodeWithTag("detail-save-tea").assertContentDescriptionEquals("Save Tea").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithContentDescription("Unsave Tea").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("detail-add-tea").performClick()
        compose.onNodeWithTag("product-back").performClick()
        compose.onNodeWithTag("search-field").assertTextContains("Tea")
        compose.onNodeWithTag("nav-cart").performClick()
        waitForTag("cart-quantity-tea")
        quantityIs(1)
        compose.onNodeWithTag("cart-line-total-tea").assertTextEquals("₹499")

        // Clearing a draft does not mean quantity zero. Cancel keeps the accepted one.
        compose.onNodeWithTag("cart-quantity-tea").performClick()
        compose.onNodeWithTag("quantity-draft").performTextClearance()
        compose.onNodeWithTag("quantity-confirm").assertIsNotEnabled()
        compose.onNodeWithText("Enter a quantity. An empty value does not remove the item.")
            .assertIsDisplayed()
        compose.onNodeWithTag("quantity-cancel").performClick()
        compose.onNodeWithTag("quantity-draft").assertDoesNotExist()
        quantityIs(1)

        // A valid explicit confirmation publishes four and recalculates the actual product price.
        compose.onNodeWithTag("cart-quantity-tea").performClick()
        compose.onNodeWithTag("quantity-draft").performTextReplacement("4")
        compose.onNodeWithTag("quantity-confirm").assertIsEnabled().performClick()
        waitForQuantity(4)
        compose.onNodeWithTag("cart-line-total-tea").assertTextEquals("₹1,996")

        // The saved surface uses the same accepted cart. A refused fifth item must not leak into it.
        compose.onNodeWithTag("nav-saved").performClick()
        waitForTag("saved-add-tea")
        compose.onNodeWithText("4 in your cart").assertIsDisplayed()
        compose.onNodeWithTag("saved-add-tea").performClick()
        waitForText("You can add up to 4 of Tea.")
        compose.onNodeWithText("You can add up to 4 of Tea.").assertIsDisplayed()
        compose.onNodeWithText("4 in your cart").assertIsDisplayed()
        compose.onNodeWithTag("nav-cart").performClick()
        waitForQuantity(4)
        compose.onNodeWithTag("cart-screen").performScrollToNode(hasTestTag("cart-total"))
        compose.onNodeWithTag("cart-total").assertTextEquals("₹1,996")

        // The shopper explicitly sees that this is a local demo before submitting.
        compose.onNodeWithTag("checkout").assertIsDisplayed().performClick()
        compose.onNodeWithText("This is a local demo order. No payment is taken and nothing will be shipped.")
            .assertIsDisplayed()
        compose.onNodeWithText("4 × Tea").assertIsDisplayed()
        compose.onNodeWithTag("place-order").performClick()
        waitForTag("order-receipt")
        compose.onNodeWithText("Demo order placed").assertIsDisplayed()
        compose.onNodeWithTag("order-id").assertTextEquals("Order CG-00001")
        compose.onNodeWithText("Quantity 4").assertIsDisplayed()
        compose.onNodeWithTag("order-receipt").performScrollToNode(hasTestTag("order-continue"))
        compose.onNodeWithTag("order-continue").performClick()
        compose.onNodeWithTag("nav-cart").performClick()
        waitForTag("cart-browse")
        compose.onNodeWithTag("cart-quantity-tea").assertDoesNotExist()
        compose.onNodeWithTag("checkout").assertDoesNotExist()

        // Checkout consumes accepted cart lines without deleting the reader's saved collection.
        compose.onNodeWithTag("nav-saved").performClick()
        waitForTag("saved-add-tea")
        compose.onNodeWithText("4 in your cart").assertDoesNotExist()
        compose.onNodeWithTag("saved-add-tea").assertIsEnabled()
    }

    private fun quantityIs(expected: Int) = compose.onNodeWithTag("cart-quantity-tea").assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Quantity $expected"),
    )

    private fun waitForQuantity(expected: Int) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("cart-quantity-tea").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) { "" } == "Quantity $expected"
            }
        }
        quantityIs(expected)
    }

    private fun waitForTag(tag: String) {
        try { compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        } } catch(failure:Exception) { throw AssertionError("Missing $tag\n"+compose.onRoot().printToString(),failure) }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
