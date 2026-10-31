package com.onyx.app.ui.common

import com.onyx.app.ui.design.onyxPage

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import com.onyx.app.ui.localization.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.onyx.app.ui.design.OnyxFormSection
import com.onyx.app.ui.design.OnyxIcon
import com.onyx.app.ui.design.OnyxPageHeader
import com.onyx.app.ui.design.OnyxRow
import com.onyx.app.ui.design.OnyxRowDivider
import com.onyx.app.ui.design.Symbols
import com.onyx.app.ui.design.onyxClickable
import com.onyx.app.ui.design.onyxTappable
import com.onyx.app.ui.theme.LocalOnyxTheme

/** Les trois pourboires proposés. */
private val TIP_IDS = listOf("tip_small" to "A coffee", "tip_medium" to "A lunch", "tip_large" to "A dinner")

/**
 * Réglages → À propos → Soutenir Onyx (`TipJarView`).
 *
 * Un pourboire unique par la boutique. Il ne débloque rien : tout est déjà là.
 */
@Composable
fun TipJarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOnyxTheme.current
    val context = LocalContext.current
    val activity = context as? Activity

    var details by remember { mutableStateOf<List<ProductDetails>>(emptyList()) }
    var state by remember { mutableStateOf(State.LOADING) }
    var buying by remember { mutableStateOf<String?>(null) }

    val listener = remember {
        PurchasesUpdatedListener { result: BillingResult, purchases: List<Purchase>? ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
                state = State.THANKED
            }
            buying = null
        }
    }

    val client = remember {
        BillingClient.newBuilder(context).setListener(listener).enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        ).build()
    }

    LaunchedEffect(Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    state = State.UNAVAILABLE
                    return
                }
                val query = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        TIP_IDS.map { (id, _) ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(id)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        },
                    )
                    .build()
                client.queryProductDetailsAsync(query) { result2, list ->
                    if (result2.responseCode == BillingClient.BillingResponseCode.OK && !list.isNullOrEmpty()) {
                        details = list
                        state = State.READY
                    } else {
                        state = State.UNAVAILABLE
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                state = State.UNAVAILABLE
            }
        })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onyxPage(theme),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            OnyxPageHeader(title = "Support Onyx", onBack = onBack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OnyxIcon(Symbols.HeartFill, tint = theme.textColor, size = 34.dp)
                BasicText(
                    text = "Onyx is made by one small company, with no ads, no accounts and no tracking. If it's useful to you, a tip helps keep it going.",
                    style = theme.subheadline.copy(color = theme.secondaryText, textAlign = TextAlign.Center),
                )
            }

            when (state) {
                State.LOADING -> OnyxFormSection {
                    OnyxRow { BasicText("Loading…", style = theme.body.copy(color = theme.secondaryText)) }
                }

                State.UNAVAILABLE -> OnyxFormSection {
                    OnyxRow {
                        OnyxIcon(Symbols.HeartFill, tint = theme.secondaryText, size = 16.dp)
                        BasicText(
                            text = "Tips aren't available right now. Nothing's wrong on your side; every feature is already yours.",
                            style = theme.footnote.copy(color = theme.secondaryText),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                State.THANKED -> OnyxFormSection {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicText("Thank you!", style = theme.title2.copy(color = theme.textColor, fontWeight = FontWeight.SemiBold))
                        BasicText("It really does help.", style = theme.body.copy(color = theme.secondaryText))
                        BasicText(
                            text = "Done",
                            style = theme.body.copy(color = theme.accent),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .onyxClickable { state = State.READY },
                        )
                    }
                }

                State.READY -> OnyxFormSection(
                    footer = "A one-time tip through the store. It doesn't unlock anything: every feature is already yours.",
                ) {
                    details.forEachIndexed { index, product ->
                        if (index > 0) OnyxRowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onyxTappable { buying = product.productId }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OnyxIcon(
                                symbol = listOf(Symbols.HeartFill, Symbols.Sparkles, Symbols.Bolt)[index % 3],
                                tint = theme.accent,
                                size = 20.dp,
                            )
                            BasicText(
                                text = product.name,
                                style = theme.body.copy(color = theme.textColor),
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            BasicText(
                                text = product.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
                                style = theme.body.copy(color = theme.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }

    // L'achat, quand une ligne a été touchée.
    LaunchedEffect(buying, details) {
        val product = details.firstOrNull { it.productId == buying } ?: return@LaunchedEffect
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()
        if (activity != null) {
            client.launchBillingFlow(activity, params)
        } else {
            state = State.UNAVAILABLE
        }
    }
}

/** Où en est le bocal. */
private enum class State { LOADING, READY, UNAVAILABLE, THANKED }
