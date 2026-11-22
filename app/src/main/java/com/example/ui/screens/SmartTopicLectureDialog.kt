package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.ExamType
import com.example.data.YksQuestion

/**
 * Akıllı Konu Anlatımı Diyaloğu (v1.5.1):
 * v1.4.9 public imzasını ve test yardımcılarını (`buildYoutubeEmbedHtml`, `formatLessonTimestamp`)
 * koruyarak `com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.1` tabanlı yerleşik native
 * `YouTubePlayerView` oynatıcısını, sıfır-reload `seekTo` bölüm atlama çiplerini, hız/sar/döngü
 * kontrollerini ve alternatif hoca değiştiricisini açar.
 */
@Composable
internal fun SmartTopicLectureDialog(
    question: YksQuestion,
    examType: String,
    subjectName: String,
    fallbackTopic: String,
    onDismiss: () -> Unit
) {
    val resolvedExamType = ExamType.entries.firstOrNull {
        it.name.equals(examType.trim(), ignoreCase = true) ||
            it.label.equals(examType.trim(), ignoreCase = true)
    } ?: ExamType.TYT
    val effectiveSubjectName = SmartLessonCatalog.subject(examType, question.subject)?.name
        ?: subjectName.ifBlank { question.subject }
    val effectiveTopicName = question.topicHeader.ifBlank { fallbackTopic }

    TopicLecturePlayerModal(
        question = question,
        examType = resolvedExamType,
        subjectName = effectiveSubjectName,
        topicName = effectiveTopicName,
        onDismiss = onDismiss
    )
}

internal fun buildYoutubeEmbedHtml(videoId: String, startSeconds: Int, endSeconds: Int? = null): String {
    require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
    require(startSeconds >= 0 && (endSeconds == null || endSeconds > startSeconds))
    val end = endSeconds?.let { ",end:$it" }.orEmpty()
    val endBoundary = endSeconds?.toString() ?: "null"
    return """
        <!doctype html><html lang="tr"><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#000}</style>
        </head><body><div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player, positionConfirmed=false, seekAttempts=0;
        var requestedStart=$startSeconds, segmentEnd=$endBoundary;
        function checkInitialPosition(){
          if(positionConfirmed) return true;
          var current=player.getCurrentTime();
          if(Number.isFinite(current) && current>=Math.max(0,requestedStart-2) && current<=requestedStart+15){
            positionConfirmed=true;
            console.log('SORULAB_POSITION_CONFIRMED:'+current);
            return true;
          }
          if(seekAttempts<3){
            seekAttempts++;
            player.seekTo(requestedStart,true);
          }else{
            player.pauseVideo();
            console.log('SORULAB_ERROR:POSITION');
          }
          return false;
        }
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('player',{
            width:'100%',height:'100%',videoId:'$videoId',
            playerVars:{playsinline:1,autoplay:1,start:$startSeconds$end,
              origin:'https://github.com',rel:0},
            events:{
              onReady:function(e){
                console.log('SORULAB_READY');
                seekAttempts=1;e.target.seekTo(requestedStart,true);e.target.playVideo();
                setInterval(function(){
                  if(player && player.getPlayerState()===YT.PlayerState.PLAYING){
                    if(segmentEnd!==null && player.getCurrentTime()>=segmentEnd){
                      player.pauseVideo();
                    }else if(checkInitialPosition()){
                      console.log('SORULAB_TIME:'+player.getCurrentTime());
                    }
                  }
                },3000);
              },
              onStateChange:function(e){
                if(e.data===YT.PlayerState.PLAYING) checkInitialPosition();
              },
              onAutoplayBlocked:function(){console.log('SORULAB_BLOCKED');},
              onError:function(e){console.log('SORULAB_ERROR:'+e.data);}
            }
          });
        }
        </script></body></html>
    """.trimIndent()
}

internal fun formatLessonTimestamp(seconds: Int): String {
    require(seconds >= 0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, remainder)
        else "%d:%02d".format(java.util.Locale.ROOT, minutes, remainder)
}
