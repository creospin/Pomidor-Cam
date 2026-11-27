package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.LanProtocol
import com.example.data.PomidorSettings
import com.example.i18n.AppLocaleHelper.tr
import com.example.i18n.LocalIsRussian
import com.example.network.PcScriptProvider
import com.example.ui.components.BlueGradientButton
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.IceText
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MutedSlateText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PcBridgeScreen(
    settings: PomidorSettings,
    lastLanStatus: String,
    pythonScript: String,
    isBusy: Boolean,
    onSaveEndpoint: (String, String, LanProtocol) -> Unit,
    onPingPc: () -> Unit,
    onTestBreakOverlay: () -> Unit,
    onSaveTextToUri: (android.net.Uri, String, String) -> Unit,
    onShareFile: (String, String, String) -> android.content.Intent?,
    onCopiedNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRu = LocalIsRussian.current
    var hostInput by remember { mutableStateOf(settings.pcHost) }
    var portInput by remember { mutableStateOf(settings.pcPort.toString()) }
    var selectedProtocol by remember { mutableStateOf(settings.lanProtocol) }

    LaunchedEffect(settings.pcHost, settings.pcPort, settings.lanProtocol) {
        hostInput = settings.pcHost
        portInput = settings.pcPort.toString()
        selectedProtocol = settings.lanProtocol
    }

    val savePyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-python")
    ) { uri ->
        if (uri != null) {
            onSaveTextToUri(
                uri,
                pythonScript,
                tr(isRu, "Script ${PcScriptProvider.SCRIPT_FILENAME} saved", "Скрипт ${PcScriptProvider.SCRIPT_FILENAME} сохранён")
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = tr(isRu, "PC Bridge (Ubuntu / Windows)", "ПК-мост (Ubuntu / Windows)"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IceText
                )
                Text(
                    text = tr(
                        isRu,
                        "Receives break overlay triggers and workday reports over local Wi-Fi / LAN",
                        "Приём сигнала о 5-минутном отдыхе и выгрузка отчётов по локальной сети"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlateText
                )
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Lan, contentDescription = null, tint = SkyBlue)
                        Text(
                            text = tr(isRu, "PC Address on Wi-Fi / LAN", "Адрес компьютера в Wi-Fi / LAN"),
                            style = MaterialTheme.typography.titleMedium,
                            color = IceText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text(tr(isRu, "PC IP Address", "IP компьютера")) },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("pc_host_input")
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text(tr(isRu, "Port", "Порт")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pc_port_input")
                        )
                    }

                    Text(
                        text = tr(isRu, "Signal Protocol", "Протокол сигнала"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSlateText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LanProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = selectedProtocol == proto,
                                onClick = { selectedProtocol = proto },
                                label = { Text(proto.label(isRu), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalBlue,
                                    selectedLabelColor = IceText,
                                    containerColor = SlateCard,
                                    labelColor = MutedSlateText
                                )
                            )
                        }
                    }

                    BlueGradientButton(
                        onClick = { onSaveEndpoint(hostInput, portInput, selectedProtocol) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_pc_endpoint_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr(isRu, "Save PC Settings", "Сохранить параметры ПК"), fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPingPc,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ping_pc_button"),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Rounded.NetworkCheck, contentDescription = null, tint = MintGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(isRu, "Ping PC", "Ping ПК"), color = IceText)
                        }
                        OutlinedButton(
                            onClick = onTestBreakOverlay,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_overlay_pc_button"),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(Icons.Rounded.Monitor, contentDescription = null, tint = SkyBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(isRu, "Test Overlay", "Тест заставки"), color = IceText)
                        }
                    }

                    val statusValue = lastLanStatus.ifBlank { tr(isRu, "Waiting for signal", "Ожидание сигнала") }
                    Text(
                        text = "${tr(isRu, "Status", "Статус")}: $statusValue",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyBlue
                    )
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = tr(
                            isRu,
                            "Fullscreen Break Script (Ubuntu & Windows)",
                            "Скрипт заставки для Ubuntu и Windows"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = IceText
                    )
                    Text(
                        text = tr(
                            isRu,
                            "1. Copy or save ${PcScriptProvider.SCRIPT_FILENAME} to your PC.\n" +
                                "2. Ubuntu: sudo apt install python3-tk && python3 ${PcScriptProvider.SCRIPT_FILENAME}\n" +
                                "3. Windows: python ${PcScriptProvider.SCRIPT_FILENAME}\n" +
                                "4. On Ubuntu & Windows, the fullscreen overlay closes on a SINGLE Esc press.",
                            "1. Скопируйте или сохраните обновлённый файл ${PcScriptProvider.SCRIPT_FILENAME} на ПК.\n" +
                                "2. Ubuntu: sudo apt install python3-tk && python3 ${PcScriptProvider.SCRIPT_FILENAME}\n" +
                                "3. Windows: python ${PcScriptProvider.SCRIPT_FILENAME}\n" +
                                "4. На Ubuntu/Windows заставка закрывается ОДНИМ нажатием клавиши Esc."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlateText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlueGradientButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("pomidor_pc_overlay.py", pythonScript))
                                onCopiedNotice(
                                    tr(
                                        isRu,
                                        "Python script copied to clipboard",
                                        "Python-скрипт скопирован в буфер обмена"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_python_script_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(isRu, "Copy", "Копировать"), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { savePyLauncher.launch(PcScriptProvider.SCRIPT_FILENAME) },
                            modifier = Modifier.height(48.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(
                                Icons.Rounded.SaveAlt,
                                contentDescription = tr(isRu, "Save .py", "Сохранить .py"),
                                tint = IceText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = onShareFile(
                                    PcScriptProvider.SCRIPT_FILENAME,
                                    pythonScript,
                                    "text/x-python"
                                )
                                if (intent != null) {
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            intent,
                                            tr(isRu, "Send script to PC", "Отправить скрипт на ПК")
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.height(48.dp),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = tr(isRu, "Share .py", "Поделиться .py"),
                                tint = IceText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = pythonScript,
                            style = MaterialTheme.typography.labelSmall,
                            color = IceText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
