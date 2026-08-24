package com.darkprince.vpn.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object BrandColors { val Success=Color(0xFF45E36A);val Warning=Color(0xFFE0A94A);val Danger=Color(0xFFFF4D5E) }
data class AppPalette(val id:String,val title:String,val subtitle:String,val isLight:Boolean,val background:Color,val glowA:Color,val glowB:Color,val glowAlpha:Float,val panel:Color,val scheme:ColorScheme)

private val NightScheme=darkColorScheme(primary=Color(0xFFF2B84B),onPrimary=Color(0xFF120D04),primaryContainer=Color(0xFFB87A16),onPrimaryContainer=Color.White,secondary=Color(0xFFE8B34A),onSecondary=Color.Black,background=Color.Transparent,onBackground=Color(0xFFF3F4F6),surface=Color(0xFF080D15).copy(alpha=.94f),onSurface=Color(0xFFF3F4F6),surfaceVariant=Color(0xFF111925),onSurfaceVariant=Color(0xFF98A2B3),outline=Color(0xFF273142),error=BrandColors.Danger,onError=Color.White)
val NightPalette=AppPalette("night","Ночь","Фирменная чёрно-золотая",false,Color(0xFF02050A),Color(0xFFF2B84B),Color(0xFF6E4510),.13f,Color(0xFF070C13),NightScheme)
private val SunsetScheme=darkColorScheme(primary=Color(0xFFFF8A5C),onPrimary=Color(0xFF2A1206),secondary=Color(0xFFC77DFF),background=Color.Transparent,onBackground=Color(0xFFF7EDF3),surface=Color(0xFF2A2033).copy(alpha=.82f),onSurface=Color(0xFFF7EDF3),surfaceVariant=Color(0xFF352840),onSurfaceVariant=Color(0xFFBCA6C4),outline=Color(0xFF4A3856),error=BrandColors.Danger)
val SunsetPalette=AppPalette("sunset","Закат","Тёплый оранжевый и фиолетовый",false,Color(0xFF1B1422),Color(0xFFFF7A45),Color(0xFFA855F7),.20f,Color(0xFF2A2033),SunsetScheme)
private val IndigoScheme=lightColorScheme(primary=Color(0xFF4F46E5),onPrimary=Color.White,secondary=Color(0xFF1D9BDB),background=Color.Transparent,onBackground=Color(0xFF161A38),surface=Color.White.copy(.88f),onSurface=Color(0xFF161A38),surfaceVariant=Color(0xFFE3E8F6),onSurfaceVariant=Color(0xFF5B6488),outline=Color(0xFFC9D2E8),error=Color(0xFFC2413F))
val IndigoPalette=AppPalette("indigo","Индиго","Светлая, сине-фиолетовая",true,Color(0xFFEDF1F9),Color(0xFF4F46E5),Color(0xFF1D9BDB),.10f,Color.White,IndigoScheme)
private val GraphiteScheme=lightColorScheme(primary=Color(0xFF1C1C1E),onPrimary=Color.White,secondary=Color(0xFF5A5A62),background=Color.Transparent,onBackground=Color(0xFF121214),surface=Color.White.copy(.9f),onSurface=Color(0xFF121214),surfaceVariant=Color(0xFFE7E7EA),onSurfaceVariant=Color(0xFF6A6A72),outline=Color(0xFFD3D3D8),error=Color(0xFFB23A38))
val GraphitePalette=AppPalette("graphite","Графит","Светлая, почти без цвета",true,Color(0xFFF3F3F5),Color(0xFF9A9AA4),Color(0xFF74747E),.14f,Color.White,GraphiteScheme)
val AppPalettes=listOf(NightPalette,SunsetPalette,IndigoPalette,GraphitePalette)
fun paletteById(id:String?)=AppPalettes.firstOrNull{it.id==id}?:NightPalette
val LocalPalette=staticCompositionLocalOf{NightPalette}
@Composable fun AppTheme(palette:AppPalette=NightPalette,content:@Composable()->Unit){CompositionLocalProvider(LocalPalette provides palette){MaterialTheme(colorScheme=palette.scheme,content=content)}}
