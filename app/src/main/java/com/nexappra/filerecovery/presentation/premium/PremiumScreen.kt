package com.nexappra.filerecovery.presentation.premium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

private val PremiumBlue = Color(0xFF2563EB)
private val PremiumBlueLight = Color(0xFFEFF6FF)
private val PremiumGold = Color(0xFFFFB800)
private val PremiumGreen = Color(0xFF22C55E)

@Composable
fun PremiumRoute(
    onClose: () -> Unit,
    onContinueWithFree: () -> Unit,
    onContinueWithPremium: (PremiumPlan) -> Unit,
    viewModel: PremiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    PremiumScreen(
        selectedPlan = uiState.selectedPlan,
        onPlanSelected = viewModel::selectPlan,
        onClose = onClose,
        onContinueWithFree = onContinueWithFree,
        onContinueWithPremium = {
            viewModel.activatePremium {
                onContinueWithPremium(uiState.selectedPlan)
            }
        }
    )
}

@Composable
fun PremiumScreen(
    selectedPlan: PremiumPlan,
    onPlanSelected: (PremiumPlan) -> Unit,
    onClose: () -> Unit,
    onContinueWithFree: () -> Unit,
    onContinueWithPremium: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {

        PremiumTopBar(
            onClose = onClose
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 16.dp,
                    bottom = 20.dp
                )
                .navigationBarsPadding()
        ) {

            PremiumHeader()

            Spacer(modifier = Modifier.height(18.dp))

            PremiumFeatureList()

            Spacer(modifier = Modifier.height(18.dp))

            PremiumComparisonTable()

            Spacer(modifier = Modifier.height(18.dp))

            PremiumPlans(
                selectedPlan = selectedPlan,
                onPlanSelected = onPlanSelected
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinueWithPremium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 1.dp
                )
            ) {
                Text(
                    text = "Continue with Premium",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            TextButton(
                onClick = onContinueWithFree,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text(
                    text = "Continue with Free",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PremiumTopBar(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.45f
            )
        ) {

            IconButton(
                onClick = onClose,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close premium screen",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumHeader() {

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        PremiumCrownBadge()

        Spacer(modifier = Modifier.height(13.dp))

        Text(
            text = "Unlock File Recovery Premium",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "More powerful recovery tools with fewer interruptions.",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PremiumCrownBadge() {

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(PremiumGold),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = "♛",
            color = Color.White,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PremiumFeatureList() {

    val features = listOf(
        "Deep scanning enhancements",
        "Advanced file filters",
        "Unlimited recovery",
        "Ad-free experience",
        "Priority support"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        features.forEach { feature ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                FeatureCheck()

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = feature,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FeatureCheck() {

    Box(
        modifier = Modifier
            .size(21.dp)
            .clip(CircleShape)
            .background(PremiumBlue.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = "✓",
            color = PremiumBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PremiumComparisonTable() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {

        Column {

            ComparisonHeader()

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            ComparisonRow(
                feature = "Quick Scan",
                freeAvailable = true,
                premiumAvailable = true
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            ComparisonRow(
                feature = "Deep Scan",
                freeAvailable = false,
                premiumAvailable = true
            )
        }
    }
}

@Composable
private fun ComparisonHeader() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "Feature",
            modifier = Modifier.weight(1.55f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Free",
            modifier = Modifier.weight(0.75f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Premium",
            modifier = Modifier.weight(0.85f),
            color = PremiumBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ComparisonRow(
    feature: String,
    freeAvailable: Boolean,
    premiumAvailable: Boolean
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = feature,
            modifier = Modifier.weight(1.55f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        ComparisonAvailability(
            available = freeAvailable,
            availableColor = PremiumGreen,
            modifier = Modifier.weight(0.75f)
        )

        ComparisonAvailability(
            available = premiumAvailable,
            availableColor = PremiumBlue,
            modifier = Modifier.weight(0.85f)
        )
    }
}

@Composable
private fun ComparisonAvailability(
    available: Boolean,
    availableColor: Color,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = if (available) "✓" else "—",
            color = if (available) {
                availableColor
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            fontSize = if (available) 19.sp else 17.sp,
            fontWeight = if (available) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PremiumPlans(
    selectedPlan: PremiumPlan,
    onPlanSelected: (PremiumPlan) -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        MonthlyPlanCard(
            selected = selectedPlan == PremiumPlan.MONTHLY,
            onClick = {
                onPlanSelected(PremiumPlan.MONTHLY)
            },
            modifier = Modifier.weight(1f)
        )

        YearlyPlanCard(
            selected = selectedPlan == PremiumPlan.YEARLY,
            onClick = {
                onPlanSelected(PremiumPlan.YEARLY)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MonthlyPlanCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlanSurface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "\$3.99",
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "per month",
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun YearlyPlanCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlanSurface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BEST VALUE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "\$19.99",
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "per year · ",
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )

                Text(
                    text = "\$1.67/mo",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlanSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        content()
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    widthDp = 390,
    heightDp = 844
)
@Composable
private fun PremiumScreenPreview() {

    MaterialTheme {
        PremiumScreen(
            selectedPlan = PremiumPlan.YEARLY,
            onPlanSelected = {},
            onClose = {},
            onContinueWithFree = {},
            onContinueWithPremium = {}
        )
    }
}