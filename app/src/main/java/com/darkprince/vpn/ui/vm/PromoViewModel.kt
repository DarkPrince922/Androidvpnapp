package com.darkprince.vpn.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darkprince.vpn.data.api.dto.ActiveDiscountDto
import com.darkprince.vpn.data.api.dto.GroupDiscountsDto
import com.darkprince.vpn.data.api.dto.LoyaltyTiersDto
import com.darkprince.vpn.data.api.dto.PromoOfferDto
import com.darkprince.vpn.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromoUiState(
    val loading: Boolean = true,
    val offers: List<PromoOfferDto> = emptyList(),
    val discount: ActiveDiscountDto? = null,
    val group: GroupDiscountsDto? = null,
    val loyalty: LoyaltyTiersDto? = null,
    val claiming: Long? = null,
    val error: String? = null,
    val info: String? = null,
)

class PromoViewModel : ViewModel() {
    private val repo = ServiceLocator.promoRepository

    private val _state = MutableStateFlow(PromoUiState())
    val state: StateFlow<PromoUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val offers = async { repo.offers() }
            val discount = async { repo.activeDiscount() }
            val group = async { repo.groupDiscounts() }
            val loyalty = async { repo.loyalty() }
            _state.update {
                it.copy(
                    loading = false,
                    offers = offers.await(),
                    discount = discount.await(),
                    group = group.await(),
                    loyalty = loyalty.await(),
                )
            }
        }
    }

    fun claim(offer: PromoOfferDto) {
        _state.update { it.copy(claiming = offer.id, error = null, info = null) }
        viewModelScope.launch {
            repo.claim(offer.id)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            claiming = null,
                            info = when {
                                result.discountPercent != null ->
                                    "Скидка ${result.discountPercent}% активирована — она уже в ценах тарифов"
                                else -> "Готово: доступ к дополнительным серверам открыт"
                            },
                        )
                    }
                    refresh()
                }
                .onFailure { e -> _state.update { it.copy(claiming = null, error = e.message) } }
        }
    }
}
