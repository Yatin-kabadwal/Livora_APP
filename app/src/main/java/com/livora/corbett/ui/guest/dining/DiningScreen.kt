@file:OptIn(ExperimentalMaterial3Api::class)

package com.livora.corbett.ui.guest.dining

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.MenuItem
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiningViewModel @Inject constructor(private val resort: ResortRepository) : ViewModel() {
    private val _menu = MutableStateFlow<Load<List<MenuItem>>>(Load.Loading)
    val menu: StateFlow<Load<List<MenuItem>>> = _menu.asStateFlow()

    init { load() }

    fun load() {
        _menu.value = Load.Loading
        viewModelScope.launch {
            _menu.value = when (val r = resort.menu()) {
                is ApiResult.Success -> Load.Ready(r.data)
                is ApiResult.Failure -> Load.Failed(r.message)
            }
        }
    }
}

@Composable
fun DiningScreen(onBack: () -> Unit, vm: DiningViewModel = hiltViewModel()) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar("Dining", onBack)
        LoadBox(menu, onRetry = { vm.load() }, skeleton = { ListSkeleton(5, 90.dp) }) { dishes ->
            if (dishes.isEmpty()) {
                EmptyState(Icons.Filled.Restaurant, "Menu coming soon", "Ask the resort about today's dining options.")
            } else {
                val grouped = dishes.groupBy { it.category.ifBlank { "Menu" } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            "Freshly prepared at the resort. In-room ordering is available to guests during their stay.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    grouped.forEach { (cat, list) ->
                        item(key = "h-$cat") { Text(Fmt.titleCase(cat), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp)) }
                        items(list, key = { it.id }) { m -> DishRow(m) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishRow(m: MenuItem) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!m.imageUrl.isNullOrBlank()) {
                RemoteImage(m.imageUrl, m.name, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(if (m.isVeg) Brand.success else Brand.danger))
                    Spacer(Modifier.width(8.dp))
                    Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (m.isSignature) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill("Signature", Gold)
                    }
                }
                m.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(Fmt.money(m.price), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
