package com.ydh.translator

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreenContent(
                onVideoEnd = {
                    // 영상 끝나거나 스킵 시 메인으로 이동
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun SplashScreenContent(onVideoEnd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1) VideoView (Full Bleed)
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    // 뷰 자체를 화면 전체로 MATCH_PARENT
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )

                    val videoUri = Uri.parse("android.resource://${context.packageName}/raw/my_splash_video")
                    setVideoURI(videoUri)

                    setOnCompletionListener {
                        onVideoEnd()
                    }

                    // Core logic: "Center Crop" (Full Bleed) via scale
                    setOnPreparedListener { mp ->
                        // 동영상 원본 해상도
                        val videoWidth = mp.videoWidth
                        val videoHeight = mp.videoHeight
                        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

                        // VideoView 실제 (onCreate 시점엔 width/height 구해짐)
                        val viewWidth = width.toFloat()
                        val viewHeight = height.toFloat()
                        val viewRatio = viewWidth / viewHeight

                        // Full Bleed:
                        //  - if video가 더 좁으면(비율 작으면) scaleX를 키워서 화면 채움
                        //  - if video가 더 넓으면(비율 크면) scaleY를 키워서 화면 채움
                        if (videoRatio < viewRatio) {
                            // 동영상이 더 좁으므로, scaleX ↑
                            val scale = viewRatio / videoRatio
                            scaleX = scale
                        } else {
                            // 동영상이 더 넓으므로, scaleY ↑
                            val scale = videoRatio / viewRatio
                            scaleY = scale
                        }
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2) 오른쪽 상단에 Skip 버튼
        Button(
            onClick = onVideoEnd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Skip")
        }
    }
}
