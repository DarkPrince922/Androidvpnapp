package com.darkprince.vpn.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkprince.vpn.core.model.ProxyProfile
import com.darkprince.vpn.ui.theme.*
import com.darkprince.vpn.ui.vm.HomeViewModel
import kotlinx.coroutines.delay

@Composable fun ServersScreen(viewModel:HomeViewModel){val state by viewModel.state.collectAsStateWithLifecycle();val notice by viewModel.notice.collectAsStateWithLifecycle();LaunchedEffect(notice){val n=notice?:return@LaunchedEffect;delay(if(n.ok)2500 else 4500);viewModel.consumeNotice()};Column(Modifier.fillMaxSize().padding(horizontal=16.dp)){Row(Modifier.fillMaxWidth().padding(top=8.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){Text("Серверы",style=MaterialTheme.typography.headlineSmall);Row{IconButton({viewModel.pingAll()}){Icon(Icons.Default.Speed,"Пинг")};IconButton({viewModel.refresh(true,true)}){Icon(Icons.Default.Refresh,"Обновить")}}};notice?.let{NoticeBar(it)};Spacer(Modifier.height(4.dp));if(state.servers.isEmpty())Text("Список серверов пуст. Проверьте подписку.") else LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp),contentPadding=PaddingValues(bottom=16.dp)){itemsIndexed(state.servers){i,s->ServerRow(s,i==state.selectedServer,state.pings[s.key],{viewModel.selectServer(i)},state.pinging)}}}}

@Composable
internal fun ServerRow(server:ProxyProfile,selected:Boolean,ping:Long?,onClick:()->Unit,pinging:Boolean=false){
    val accent=MaterialTheme.colorScheme.primary
    val border by animateColorAsState(if(selected)accent.copy(.7f) else MaterialTheme.colorScheme.outline.copy(.45f),tween(200),label="serverBorder")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(selected)accent.copy(.055f) else MaterialTheme.colorScheme.surface).border(1.dp,border,RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
        EmojiTile(leadingEmoji(server.name)?:"🌐",accent,Modifier.size(38.dp));Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)){Text(nameWithoutEmoji(server.name),style=MaterialTheme.typography.titleSmall,maxLines=1);server.serverDescription?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)};Spacer(Modifier.height(4.dp));ProtocolBadges(server)}
        Spacer(Modifier.width(6.dp));when{ping!=null->PingChip(ping);pinging->PingPendingChip()};if(selected){Spacer(Modifier.width(5.dp));Icon(Icons.Default.CheckCircle,"Выбран",tint=accent,modifier=Modifier.size(18.dp))}
    }
}
