package com.ydh.translator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.annotation.RequiresApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.ydh.translator.ui.theme.TranslatorTheme

/**
 * 날짜 + 원문 + 번역문 + 키워드
 */
data class TranslationHistory(
    val date: LocalDate,
    val original: String,
    val translated: String,
    val keyword: String
)

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TranslatorTheme {
                TranslatorScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // ----------------
    // TTS 초기화
    // ----------------
    val tts = remember {
        var tempTTS: TextToSpeech? = null
        tempTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tempTTS?.setLanguage(Locale.KOREAN)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e("TTS", "해당 언어는 지원되지 않습니다.")
                }
            } else {
                Log.e("TTS", "TTS 초기화 실패")
            }
        }
        tempTTS
    }
    DisposableEffect(Unit) {
        onDispose {
            tts?.shutdown()
        }
    }

    // ----------------
    // 언어 목록, 상태
    // ----------------
    val languageList = listOf(
        "Korean" to "ko",
        "English" to "en",
        "Japanese" to "ja",
        "Chinese (Simplified)" to "zh-CN",
        "Chinese (Traditional)" to "zh-TW",
        "French" to "fr",
        "German" to "de",
        "Spanish" to "es",
        "Italian" to "it",
        "Russian" to "ru",
        "Arabic" to "ar",
        "Portuguese" to "pt",
        "Dutch" to "nl",
        "Swedish" to "sv",
        "Danish" to "da",
        "Finnish" to "fi",
        "Polish" to "pl",
        "Turkish" to "tr",
        "Vietnamese" to "vi",
        "Thai" to "th"
    )

    var originalText by remember { mutableStateOf("") }
    var recognizedText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }

    var selectedSourceLanguage by remember { mutableStateOf(languageList[0]) }
    var selectedTargetLanguage by remember { mutableStateOf(languageList[1]) }

    // 구글 번역 API 키
    val apiKey = ""

    var sttStatusMessage by remember { mutableStateOf("") }
    val historyList = remember { mutableStateListOf<TranslationHistory>() }

    // ----------------
    // Drawer & Scope
    // ----------------
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // ----------------
    // 권한 런처들
    // ----------------
    // 오디오(Record Audio)
    val requestAudioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startSpeechToText(
                    context = context,
                    onReady = { sttStatusMessage = "음성을 듣고 있어요..." },
                    onEnd = { sttStatusMessage = "음성 인식 중..." },
                    onResult = { text ->
                        sttStatusMessage = ""
                        recognizedText = ""

                        doTranslateAndSaveHistory(
                            original = text,
                            sourceLang = selectedSourceLanguage.second,
                            targetLang = selectedTargetLanguage.second,
                            apiKey = apiKey,
                            tts = tts,
                            historyList = historyList
                        ) { translatedText = it }

                        // 번역 완료 후 5초 배너 표시
                        var showBanners = true
                    },
                    onError = { err ->
                        sttStatusMessage = ""
                        Log.e("STT", err)
                    }
                )
            } else {
                Log.e("STT", "오디오 권한 거부됨")
            }
        }

    // TakePicturePreview (이미지 캡처)
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            recognizedText = ""
            performOcrAndTranslate(
                bitmap = bitmap,
                sourceLang = selectedSourceLanguage.second,
                targetLang = selectedTargetLanguage.second,
                apiKey = apiKey,
                onOcrSuccess = { recognized ->
                    recognizedText = recognized
                },
                onTranslateSuccess = { translated ->
                    translatedText = translated
                    tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, null)

                    val key = recognizedText.take(10).replace("\n", " ")
                    historyList.add(
                        TranslationHistory(
                            date = LocalDate.now(),
                            original = recognizedText,
                            translated = translated,
                            keyword = "$key..."
                        )
                    )
                    // 번역 완료 후 5초 배너 표시
                    var showBanners = true
                },
                onError = { err ->
                    translatedText = err
                }
            )
        }
    }

    // 카메라 권한
    val requestCameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePhotoLauncher.launch(null)
            } else {
                Log.e("Camera", "카메라 권한 거부됨")
            }
        }

    val scrollState = rememberScrollState()

    // ----------------
    // 배너 표시 상태 + 5초 뒤 자동 off
    // ----------------
    var showBanners by remember { mutableStateOf(false) }

    LaunchedEffect(showBanners) {
        if (showBanners) {
            delay(5000) // 5초
            showBanners = false
        }
    }

    // ----------------
    // UI (배경 GIF 추가)
    // ----------------
    Box(modifier = Modifier.fillMaxSize()) {

        // 1) 화면 전체에 GIF 배경
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(R.drawable.my_lantern) // 배경 GIF
                .decoderFactory(ImageDecoderDecoder.Factory()) // Android 28+
                .build(),
            contentDescription = "Background GIF",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2) 실제 콘텐츠(기존 UI)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // 드로어 배경 + 폭
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "번역 히스토리",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Divider(color = Color.Gray, thickness = 1.dp)

                        val today = LocalDate.now()
                        val yesterday = today.minusDays(1)

                        val todayList = historyList.filter { it.date == today }
                        val yesterdayList = historyList.filter { it.date == yesterday }
                        val olderList = historyList.filter { it.date < yesterday }

                        DrawerSection2("오늘", todayList) { item ->
                            originalText = item.original
                            translatedText = item.translated
                            recognizedText = ""
                            scope.launch { drawerState.close() }
                        }
                        DrawerSection2("어제", yesterdayList) { item ->
                            originalText = item.original
                            translatedText = item.translated
                            recognizedText = ""
                            scope.launch { drawerState.close() }
                        }
                        DrawerSection2("이전", olderList) { item ->
                            originalText = item.original
                            translatedText = item.translated
                            recognizedText = ""
                            scope.launch { drawerState.close() }
                        }
                    }
                }
            },
            scrimColor = Color.Black.copy(alpha = 0.4f)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            // Title: GOO.T!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "GOO.T!",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open()
                                    else drawerState.close()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Transparent
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 음성 인식 중일 때 표시할 Indicator
                    STTListeningIndicator(sttStatusMessage = sttStatusMessage)

                    // 원본 언어
                    LanguageDropDownMenu(
                        labelText = "원본 언어",
                        languageList = languageList,
                        selectedLanguage = selectedSourceLanguage,
                        onLanguageSelected = { newLang -> selectedSourceLanguage = newLang }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 목표 언어
                    LanguageDropDownMenu(
                        labelText = "목표 언어",
                        languageList = languageList,
                        selectedLanguage = selectedTargetLanguage,
                        onLanguageSelected = { newLang -> selectedTargetLanguage = newLang }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 입력 TextField
                    TextField(
                        value = originalText,
                        onValueChange = { originalText = it },
                        label = { Text("번역할 텍스트 입력", color = Color.Gray) },
                        colors = TextFieldDefaults.textFieldColors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            containerColor = Color.White,
                            focusedIndicatorColor = Color(0xFF7E57C2),
                            unfocusedIndicatorColor = Color.Gray,
                            cursorColor = Color(0xFF7E57C2)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        textStyle = LocalTextStyle.current.copy(color = Color.Black),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        })
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ============================
                    // (1) 번역하기 버튼
                    // ============================
                    TrendyButton(
                        text = "번역하기",
                        onClick = {
                            recognizedText = ""
                            doTranslateAndSaveHistory(
                                original = originalText,
                                sourceLang = selectedSourceLanguage.second,
                                targetLang = selectedTargetLanguage.second,
                                apiKey = apiKey,
                                tts = tts,
                                historyList = historyList
                            ) { translatedText = it }

                            // 번역 완료 후 5초 배너 표시
                            showBanners = true

                            focusManager.clearFocus()
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 첫 번째 배너 ---
                    if (showBanners) {
                        CustomBanner(
                            imageRes = R.drawable.pett,
                            affiliateLink = "https://link.coupang.com/a/b8WCTb"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ============================
                    // (2) 음성 번역 버튼
                    // ============================
                    TrendyButton(
                        text = "음성 번역",
                        onClick = {
                            recognizedText = ""
                            val audioPerm =
                                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            if (audioPerm == PackageManager.PERMISSION_GRANTED) {
                                startSpeechToText(
                                    context = context,
                                    onReady = { sttStatusMessage = "음성을 듣고 있어요..." },
                                    onEnd = { sttStatusMessage = "음성 인식 중..." },
                                    onResult = { sttText ->
                                        sttStatusMessage = ""
                                        doTranslateAndSaveHistory(
                                            original = sttText,
                                            sourceLang = selectedSourceLanguage.second,
                                            targetLang = selectedTargetLanguage.second,
                                            apiKey = apiKey,
                                            tts = tts,
                                            historyList = historyList
                                        ) { translatedText = it }

                                        showBanners = true
                                    },
                                    onError = { err ->
                                        sttStatusMessage = ""
                                        Log.e("STT", err)
                                    }
                                )
                            } else {
                                requestAudioPermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 두 번째 배너 ---
                    if (showBanners) {
                        CustomBanner(
                            imageRes = R.drawable.gmt,
                            affiliateLink = "https://link.coupang.com/a/b8WDLZ",
                            bannerHeight = 280.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ============================
                    // (3) 이미지 번역 버튼
                    // ============================
                    TrendyButton(
                        text = "이미지 번역",
                        onClick = {
                            recognizedText = ""
                            val cameraPerm =
                                context.checkSelfPermission(Manifest.permission.CAMERA)
                            if (cameraPerm == PackageManager.PERMISSION_GRANTED) {
                                takePhotoLauncher.launch(null)
                            } else {
                                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 마지막(iframe) 배너 ---
                    if (showBanners) {
                        CoupangIframeBanner()
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // OCR 결과
                    if (recognizedText.isNotEmpty()) {
                        Text(
                            text = "인식된 텍스트 (OCR)",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = recognizedText,
                                color = Color.Black,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // 번역 결과
                    Text(
                        text = "번역 결과",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = translatedText,
                            color = Color.Black,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * (추가) 트렌디 버튼: 핑크 계열 + 라운드 모서리
 */
@Composable
fun TrendyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp), // 둥글게
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF06292)  // 예: 핑크 계열
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text = text, fontSize = 18.sp, color = Color.White)
    }
}

/**
 * 두 번째 배너를 크게 사용할 수 있도록 `bannerHeight` 파라미터 추가.
 */
@Composable
fun CustomBanner(
    imageRes: Int,
    affiliateLink: String,
    bannerHeight: Dp = 140.dp
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "쿠팡 배너",
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(affiliateLink))
                    context.startActivity(intent)
                },
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun CoupangIframeBanner() {
    val bannerWidth = 340
    val bannerHeight = 70

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()

                loadDataWithBaseURL(
                    null,
                    """
                        <html>
                        <head><meta name="referrer" content="unsafe-url" /></head>
                        <body style="margin:0;padding:0;">
                          <iframe src="https://ads-partners.coupang.com/widgets.html?id=830089&template=carousel&trackingCode=AF8443344&subId=&width=${bannerWidth}&height=${bannerHeight}&tsource="
                                  width="${bannerWidth}" height="${bannerHeight}" frameborder="0" scrolling="no"
                                  referrerpolicy="unsafe-url" browsingtopics>
                          </iframe>
                        </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight.dp)
    )
}

/**
 * 음성 인식 중일 때( sttStatusMessage != "" ) 화면 중앙에 안내문과 GIF를 표시
 */
@Composable
fun STTListeningIndicator(sttStatusMessage: String) {
    if (sttStatusMessage.isNotEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sttStatusMessage,
                color = Color.Black,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color(0xFFD1C4E9))
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(R.drawable.my_listening_gif)
                    .decoderFactory(ImageDecoderDecoder.Factory())
                    .build(),
                contentDescription = "음성을 듣고 있어요...",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 드로어 섹션 (오늘/어제/이전)
 */
@Composable
fun DrawerSection2(
    title: String,
    items: List<TranslationHistory>,
    onSelect: (TranslationHistory) -> Unit
) {
    if (items.isNotEmpty()) {
        Text(
            text = title,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp),
            color = Color(0xFF5E35B1),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        for (item in items) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(item)
                    }
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            ) {
                Text(
                    text = "• ${item.keyword}",
                    color = Color.Black
                )
            }
        }
        Divider(color = Color.Gray)
    }
}

/** (기존과 동일) 직접 입력 or 음성 -> 번역, 기록 */
@RequiresApi(Build.VERSION_CODES.O)
fun doTranslateAndSaveHistory(
    original: String,
    sourceLang: String,
    targetLang: String,
    apiKey: String,
    tts: TextToSpeech?,
    historyList: MutableList<TranslationHistory>,
    onResult: (String) -> Unit
) {
    translateText(
        text = original,
        sourceLanguage = sourceLang,
        targetLanguage = targetLang,
        apiKey = apiKey,
        onSuccess = { translated ->
            onResult(translated)
            tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, null)

            val key = original.take(10).replace("\n", " ")
            historyList.add(
                TranslationHistory(
                    date = LocalDate.now(),
                    original = original,
                    translated = translated,
                    keyword = "$key..."
                )
            )
        },
        onError = { err ->
            onResult("번역 실패: $err")
        }
    )
}

/** (기존) 이미지 -> OCR -> 번역 */
@RequiresApi(Build.VERSION_CODES.O)
fun performOcrAndTranslate(
    bitmap: Bitmap,
    sourceLang: String,
    targetLang: String,
    apiKey: String,
    onOcrSuccess: (String) -> Unit,
    onTranslateSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            val recognized = visionText.text
            if (recognized.isBlank()) {
                onError("문자가 인식되지 않았습니다.")
                return@addOnSuccessListener
            }
            onOcrSuccess(recognized)

            translateText(
                text = recognized,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                apiKey = apiKey,
                onSuccess = onTranslateSuccess,
                onError = { err -> onError("번역 실패: $err") }
            )
        }
        .addOnFailureListener {
            onError("OCR 인식 실패: ${it.message}")
        }
}

/** (기존) 구글 번역 API 호출 */
fun translateText(
    text: String,
    sourceLanguage: String,
    targetLanguage: String,
    apiKey: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val url = HttpUrl.Builder()
        .scheme("https")
        .host("translation.googleapis.com")
        .addPathSegment("language")
        .addPathSegment("translate")
        .addPathSegment("v2")
        .addQueryParameter("key", apiKey)
        .addQueryParameter("q", text)
        .addQueryParameter("source", sourceLanguage)
        .addQueryParameter("target", targetLanguage)
        .addQueryParameter("format", "text")
        .build()

    val request = Request.Builder().url(url).get().build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "Network error")
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                onError("Response not successful: ${response.code}")
                return
            }
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                onError("Empty response body")
                return
            }
            try {
                val jsonObject = JSONObject(body)
                val translations = jsonObject
                    .getJSONObject("data")
                    .getJSONArray("translations")
                val translatedText = translations
                    .getJSONObject(0)
                    .getString("translatedText")

                onSuccess(translatedText)
            } catch (e: Exception) {
                onError("파싱 에러: ${e.message}")
            }
        }
    })
}

/** (기존) 음성 인식 (STT) */
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
fun startSpeechToText(
    context: Context,
    onReady: () -> Unit,
    onEnd: () -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("이 기기는 음성 인식을 지원하지 않습니다.")
        return
    }

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onReady() }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onEnd() }
        override fun onError(error: Int) {
            onError("음성 인식 에러 코드: $error")
            speechRecognizer.destroy()
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onResult(matches[0])
            } else {
                onError("인식된 음성이 없습니다.")
            }
            speechRecognizer.destroy()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer.startListening(intent)
}

/** (기존) LanguageDropDownMenu */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropDownMenu(
    labelText: String,
    languageList: List<Pair<String, String>>,
    selectedLanguage: Pair<String, String>,
    onLanguageSelected: (Pair<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLanguage.first,
            onValueChange = {},
            readOnly = true,
            label = { Text(labelText, color = Color.Gray) },
            textStyle = LocalTextStyle.current.copy(color = Color.Black),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Color.White,
                focusedBorderColor = Color(0xFF7E57C2),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF7E57C2),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color(0xFF7E57C2),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languageList.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.first, color = Color.Black) },
                    onClick = {
                        onLanguageSelected(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}
