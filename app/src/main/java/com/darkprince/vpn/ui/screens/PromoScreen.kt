package com.darkprince.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.data.api.dto.LoyaltyTierDto
import com.darkprince.vpn.data.api.dto.PromoOfferDto
import com.darkprince.vpn.ui.theme.BrandColors
import com.darkprince.vpn.ui.theme.GroupCard
import com.darkprince.vpn.ui.theme.SectionHeader
import com.darkprince.vpn.ui.theme.ServiceDownBanner
import com.darkprince.vpn.ui.theme.serviceDown
import com.darkprince.vpn.ui.vm.PromoViewModel

/**
 * Скидки и бонусы: что можно забрать прямо сейчас, какая скидка действует
 * и сколько осталось до следующего уровня.
 */
@Composable
fun PromoScreen(viewModel: PromoViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val down = serviceDown()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("Скидки и бонусы", style = MaterialTheme.typography.headlineSmall)
            ServiceDownBanner(modifier = Modifier.padding(top = 10.dp))
        }
        if (state.loading && state.loyalty == null && state.offers.isEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.error?.takeIf { !down }?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        state.info?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        state.discount?.let { discount ->
            item {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Действует скидка ${discount.discountPercent}%", style = MaterialTheme.typography.titleLarge)
                        Text(
                            (discount.expiresAt?.let { "До ${cabinetDate(it)}. " } ?: "") +
                                "Цены в «Тарифах» уже с ней — скидка сработает на одну покупку.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.offers.isNotEmpty()) {
            item { SectionHeader("Для вас") }
            items(state.offers, key = { "offer-${it.id}" }) { offer ->
                OfferRow(
                    offer = offer,
                    busy = state.claiming != null || down,
                    claiming = state.claiming == offer.id,
                    onClaim = { viewModel.claim(offer) },
                )
            }
        } else if (!state.loading && state.discount == null) {
            item {
                Text(
                    "Персональных предложений пока нет. Они приходят от бота — например, когда подписка закончилась.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.group?.takeIf { it.groupName != null && hasDiscounts(it.serverPercent, it.trafficPercent, it.devicePercent, it.periodDiscounts) }?.let { group ->
            item { SectionHeader("Ваша группа") }
            item {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.groupName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            discountsText(group.serverPercent, group.trafficPercent, group.devicePercent, group.periodDiscounts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val loyalty = state.loyalty
        if (loyalty != null && loyalty.tiers.isNotEmpty()) {
            item { SectionHeader("Уровни") }
            item {
                GroupCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Вы потратили ${formatKopeks((loyalty.spentRubles * 100).toLong())}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (loyalty.nextTierName != null && loyalty.nextTierThresholdRubles != null) {
                            LinearProgressIndicator(
                                progress = { (loyalty.progressPercent / 100).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            val left = ((loyalty.nextTierThresholdRubles - loyalty.spentRubles) * 100).toLong()
                            Text(
                                "До уровня «${loyalty.nextTierName}» — ${formatKopeks(left.coerceAtLeast(0))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "У вас максимальный уровень",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.Success,
                            )
                        }
                    }
                }
            }
            items(loyalty.tiers, key = { "tier-${it.id}" }) { tier -> TierRow(tier) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun offerTitle(offer: PromoOfferDto): String = when {
    offer.effectType.equals("test_access", ignoreCase = true) -> "Пробный доступ к дополнительным серверам"
    offer.discountPercent != null -> "Скидка ${offer.discountPercent}% на подписку"
    else -> "Специальное предложение"
}

@Composable
private fun OfferRow(offer: PromoOfferDto, busy: Boolean, claiming: Boolean, onClaim: () -> Unit) {
    GroupCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(offerTitle(offer), style = MaterialTheme.typography.titleMedium)
            offer.expiresAt?.let {
                Text(
                    "Забрать до ${cabinetDate(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.Warning,
                )
            }
            Button(
                onClick = onClaim,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (claiming) "Активируем…" else "Забрать") }
        }
    }
}

@Composable
private fun TierRow(tier: LoyaltyTierDto) {
    val accent = when {
        tier.isCurrent -> MaterialTheme.colorScheme.primary
        tier.isAchieved -> BrandColors.Success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent)
                    .padding(6.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tier.name + if (tier.isCurrent) " · ваш уровень" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tier.isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "от ${formatKopeks((tier.thresholdRubles * 100).toLong())} · " +
                        discountsText(tier.serverPercent, tier.trafficPercent, tier.devicePercent, tier.periodDiscounts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hasDiscounts(server: Int, traffic: Int, device: Int, periods: Map<String, Int>) =
    server > 0 || traffic > 0 || device > 0 || periods.values.any { it > 0 }

/** «−10% на подписку, −20% от 90 дней». */
private fun discountsText(server: Int, traffic: Int, device: Int, periods: Map<String, Int>): String {
    val parts = mutableListOf<String>()
    if (server > 0) parts += "−$server% на подписку"
    periods.entries
        .mapNotNull { (days, percent) -> days.toIntOrNull()?.let { it to percent } }
        .filter { it.second > 0 }
        .sortedBy { it.first }
        .forEach { (days, percent) -> parts += "−$percent% от $days дн." }
    if (traffic > 0) parts += "−$traffic% на трафик"
    if (device > 0) parts += "−$device% на устройства"
    return parts.joinToString(", ").ifBlank { "без скидок" }
}
