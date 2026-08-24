package com.darkprince.vpn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkprince.vpn.core.model.ProxyProfile

@Composable fun SectionHeader(text:String, modifier:Modifier=Modifier)=Text(text.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.4.sp,modifier=modifier.padding(start=4.dp,bottom=8.dp))
@Composable fun GroupCard(modifier:Modifier=Modifier,content: @Composable ColumnScope.() -> Unit)=Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).border(BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.6f)),RoundedCornerShape(22.dp)),content=content)
@Composable fun RowDivider()=HorizontalDivider(Modifier.padding(start=68.dp,end=16.dp),color=MaterialTheme.colorScheme.outline.copy(.4f))
@Composable fun IconTile(icon:ImageVector,tint:Color=MaterialTheme.colorScheme.primary,modifier:Modifier=Modifier){Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(.14f)).border(1.dp,tint.copy(.35f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=tint,modifier=Modifier.size(20.dp))}}
@Composable fun EmojiTile(emoji:String,tint:Color=MaterialTheme.colorScheme.primary,modifier:Modifier=Modifier){Box(modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(.08f)).border(1.dp,tint.copy(.25f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Text(emoji,style=MaterialTheme.typography.titleLarge)}}

@Composable
fun TagChip(text:String,tint:Color=MaterialTheme.colorScheme.primary,modifier:Modifier=Modifier){
    Text(text,style=MaterialTheme.typography.labelSmall,color=tint,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,
        modifier=modifier.clip(RoundedCornerShape(7.dp)).background(tint.copy(.09f)).border(1.dp,tint.copy(.65f),RoundedCornerShape(7.dp)).padding(horizontal=8.dp,vertical=3.dp))
}

private fun protocolColor(label:String):Color=when(label.lowercase()){
    "vless","vmess","trojan","shadowsocks"->Color(0xFFE8B34A)
    "reality"->Color(0xFFC85CFF)
    "tls"->Color(0xFF3B82F6)
    "grpc"->Color(0xFFFF4D5E)
    "xhttp","ws","websocket","hysteria2"->Color(0xFF16D9F4)
    "tcp","http/2","httpupgrade"->Color(0xFFFFA62B)
    else->Color(0xFF9AA6BD)
}

@Composable
fun ProtocolBadges(server:ProxyProfile,modifier:Modifier=Modifier){
    val labels=listOfNotNull(server.protocolLabel,server.securityLabel,server.networkLabel).distinct()
    Row(modifier=modifier,horizontalArrangement=Arrangement.spacedBy(5.dp)){
        labels.forEach { label ->
            val c=protocolColor(label)
            Text(label,color=c,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,fontSize=11.sp,maxLines=1,
                modifier=Modifier.shadow(5.dp,RoundedCornerShape(6.dp),ambientColor=c,spotColor=c).clip(RoundedCornerShape(6.dp)).background(c.copy(.08f)).border(1.dp,c.copy(.8f),RoundedCornerShape(6.dp)).padding(horizontal=7.dp,vertical=2.dp))
        }
    }
}

@Composable fun PingChip(millis:Long,modifier:Modifier=Modifier){val bad=millis<0;val tint=when{bad->MaterialTheme.colorScheme.error;millis<300->BrandColors.Success;millis<700->BrandColors.Warning;else->MaterialTheme.colorScheme.error};Text(if(bad)"нет" else "$millis мс",color=tint,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelSmall,modifier=modifier)}
@Composable fun PingPendingChip(modifier:Modifier=Modifier){CircularProgressIndicator(modifier.size(12.dp),strokeWidth=1.5.dp)}
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailing != null) trailing() else {
            value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
@Composable fun CircleActionButton(icon:ImageVector,contentDescription:String,onClick:()->Unit,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.primary){Box(modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(tint.copy(.12f)).clickable(onClick=onClick),contentAlignment=Alignment.Center){Icon(icon,contentDescription,tint=tint,modifier=Modifier.size(18.dp))}}
@Composable fun NavPillItem(selected:Boolean,icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier){val a=MaterialTheme.colorScheme.primary;val bg by animateColorAsState(if(selected)a.copy(.16f) else Color.Transparent,tween(220),label="navBg");val c by animateColorAsState(if(selected)a else MaterialTheme.colorScheme.onSurfaceVariant,tween(220),label="navC");Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick=onClick).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,label,tint=c,modifier=Modifier.size(22.dp));Text(label,style=MaterialTheme.typography.labelSmall,color=c,maxLines=1)}}
fun leadingEmoji(name:String):String?{val t=name.trimStart();if(t.isEmpty())return null;val f=t.codePointAt(0);if(f in 0x1F1E6..0x1F1FF){val n=Character.charCount(f);if(t.length>n){val s=t.codePointAt(n);if(s in 0x1F1E6..0x1F1FF)return t.substring(0,n+Character.charCount(s))};return t.substring(0,n)};if(f>0x2000&&!Character.isLetterOrDigit(f))return t.substring(0,Character.charCount(f));return null}
fun nameWithoutEmoji(name:String):String{val e=leadingEmoji(name)?:return name.trim();return name.trimStart().removePrefix(e).trim().ifBlank{name.trim()}}
