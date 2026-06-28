package com.example.lightnovelcheck

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.lightnovelcheck.ui.theme.LightNovelCheckTheme
import com.google.gson.Gson // [핵심] 데이터를 문자열로 변환하는 도구
import com.google.gson.reflect.TypeToken
import java.util.UUID

// --- 데이터 클래스 (DB 관련 어노테이션 삭제됨) ---
data class Volume(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var status: String // "O", "X", "$"
)

data class BookSeries(
    val id: Long,
    val title: String,
    val volumes: List<Volume>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LightNovelCheckTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LightNovelApp()
                }
            }
        }
    }
}

// --- [핵심] 데이터 저장/불러오기 함수 (SharedPreferences 사용) ---
fun saveBookList(context: Context, list: List<BookSeries>) {
    val sharedPref = context.getSharedPreferences("MyBookData", Context.MODE_PRIVATE)
    val jsonString = Gson().toJson(list) // 리스트를 문자열로 변환
    sharedPref.edit().putString("book_list", jsonString).apply() // 저장
}

fun loadBookList(context: Context): List<BookSeries> {
    val sharedPref = context.getSharedPreferences("MyBookData", Context.MODE_PRIVATE)
    val jsonString = sharedPref.getString("book_list", null)

    return if (jsonString != null) {
        val type = object : TypeToken<List<BookSeries>>() {}.type
        Gson().fromJson(jsonString, type) // 문자열을 다시 리스트로 복구
    } else {
        emptyList() // 저장된 게 없으면 빈 리스트 반환
    }
}

@Composable
fun LightNovelApp() {
    val context = LocalContext.current
    // 앱 켜질 때 데이터 불러오기
    var bookList by remember { mutableStateOf(loadBookList(context)) }

    var selectedBookId by remember { mutableStateOf<Long?>(null) }
    val selectedBook = bookList.find { it.id == selectedBookId }

    // 데이터가 바뀔 때마다 저장하는 함수
    fun updateAndSave(newList: List<BookSeries>) {
        bookList = newList
        saveBookList(context, newList)
    }

    if (selectedBook == null) {
        MainScreen(
            bookList = bookList,
            onAddSeries = { newSeries ->
                updateAndSave(bookList + newSeries)
            },
            onBookClick = { book -> selectedBookId = book.id },
            onDeleteSeries = { idToDelete ->
                updateAndSave(bookList.filter { it.id != idToDelete })
            }
        )
    } else {
        DetailScreen(
            series = selectedBook,
            onBack = { selectedBookId = null },
            onUpdateSeries = { updatedSeries ->
                updateAndSave(bookList.map { if (it.id == updatedSeries.id) updatedSeries else it })
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    bookList: List<BookSeries>,
    onAddSeries: (BookSeries) -> Unit,
    onBookClick: (BookSeries) -> Unit,
    onDeleteSeries: (Long) -> Unit
) {
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val activity = LocalContext.current as? android.app.Activity

    BackHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }

    var searchText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Long?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newVolume by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { deleteTargetId = null }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = searchText, onValueChange = { searchText = it },
                label = { Text("책 제목 검색") }, modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showDialog = true },
                shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(56.dp)
            ) { Icon(imageVector = Icons.Filled.Add, contentDescription = "추가") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            val filteredList = if (searchText.isEmpty()) bookList else bookList.filter { it.title.contains(searchText) }
            items(filteredList.size) { index ->
                val book = filteredList[index]
                val isDeleteMode = (deleteTargetId == book.id)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                        onClick = { if (isDeleteMode) deleteTargetId = null else onBookClick(book) },
                        onLongClick = { deleteTargetId = book.id }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = book.title, style = MaterialTheme.typography.titleMedium)
                            val ownedCount = book.volumes.count { it.status == "O" }
                            Text(text = "보유: ${ownedCount} / 총 ${book.volumes.size}권", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (isDeleteMode) {
                            Button(onClick = { onDeleteSeries(book.id); deleteTargetId = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("삭제") }
                        }
                    }
                }
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("새 시리즈 추가") },
            text = { Column { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("책 제목") }); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = newVolume, onValueChange = { if (it.all { char -> char.isDigit() }) newVolume = it }, label = { Text("보유 권수 (숫자)") }) } },
            confirmButton = { Button(onClick = { if (newTitle.isNotEmpty() && newVolume.isNotEmpty()) { val count = newVolume.toInt(); val initVolumes = (1..count).map { num -> Volume(name = num.toString(), status = if (num == count) "O" else "X") }; val newBook = BookSeries(id = System.currentTimeMillis(), title = newTitle, volumes = initVolumes); onAddSeries(newBook); newTitle = ""; newVolume = ""; searchText = ""; showDialog = false } }) { Text("추가") } },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("취소") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    series: BookSeries,
    onBack: () -> Unit,
    onUpdateSeries: (BookSeries) -> Unit
) {
    BackHandler { onBack() }
    var showAddDialog by remember { mutableStateOf(false) }
    var newVolName by remember { mutableStateOf("") }
    var targetVolIndex by remember { mutableStateOf("") }
    var insertPosition by remember { mutableStateOf("after") }
    var deleteTargetVolId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { deleteTargetVolId = null }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "뒤로") }
            Text(text = series.title, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(series.volumes.size) { index ->
                val vol = series.volumes[index]
                val btnColor = when (vol.status) { "O" -> MaterialTheme.colorScheme.primaryContainer; "$" -> Color(0xFFFFD700); else -> MaterialTheme.colorScheme.surfaceVariant }
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.aspectRatio(1f).combinedClickable(
                            onClick = { val newStatus = when (vol.status) { "X" -> "O"; "O" -> "$"; else -> "X" }; val updatedVolumes = series.volumes.toMutableList(); updatedVolumes[index] = vol.copy(status = newStatus); onUpdateSeries(series.copy(volumes = updatedVolumes)) },
                            onLongClick = { deleteTargetVolId = vol.id }
                        ),
                        shape = MaterialTheme.shapes.extraSmall, color = btnColor, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = if (vol.status == "X") vol.name else "${vol.name}\n${vol.status}", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Black)
                        }
                    }
                    if (deleteTargetVolId == vol.id) {
                        Popup(alignment = Alignment.BottomCenter, onDismissRequest = { deleteTargetVolId = null }) {
                            Button(onClick = { val updatedVolumes = series.volumes.toMutableList(); updatedVolumes.removeAt(index); onUpdateSeries(series.copy(volumes = updatedVolumes)); deleteTargetVolId = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.padding(top = 40.dp)) { Text("삭제", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item { Button(onClick = { showAddDialog = true }, modifier = Modifier.aspectRatio(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("+") } }
        }
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("책/외전 삽입") },
            text = { Column { OutlinedTextField(value = newVolName, onValueChange = { newVolName = it }, label = { Text("책 번호 (예: 15-ss)") }); Spacer(modifier = Modifier.height(8.dp)); Text("기준 책 번호 (비우면 맨 뒤)", style = MaterialTheme.typography.bodySmall); OutlinedTextField(value = targetVolIndex, onValueChange = { targetVolIndex = it }, label = { Text("예: 15") }); Spacer(modifier = Modifier.height(8.dp)); Row { FilterChip(selected = insertPosition == "before", onClick = { insertPosition = "before" }, label = { Text("앞") }); Spacer(modifier = Modifier.width(8.dp)); FilterChip(selected = insertPosition == "after", onClick = { insertPosition = "after" }, label = { Text("뒤") }) } } },
            confirmButton = { Button(onClick = { val newVol = Volume(name = newVolName, status = "X"); val updatedVolumes = series.volumes.toMutableList(); if (targetVolIndex.isEmpty()) updatedVolumes.add(newVol) else { val index = updatedVolumes.indexOfFirst { it.name == targetVolIndex }; if (index != -1) { if (insertPosition == "after") updatedVolumes.add(index + 1, newVol) else updatedVolumes.add(index, newVol) } else updatedVolumes.add(newVol) }; onUpdateSeries(series.copy(volumes = updatedVolumes)); newVolName = ""; targetVolIndex = ""; showAddDialog = false }) { Text("추가") } },
            dismissButton = { Button(onClick = { showAddDialog = false }) { Text("취소") } }
        )
    }
}