package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.example.viewmodel.DenemeTopicProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.CustomQuestionRecord
import com.example.data.ExamType
import com.example.data.MockExamItem
import com.example.data.SoruLabCatalog
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.viewmodel.DenemeBlueprints
import com.example.viewmodel.DenemeDifficulty
import com.example.viewmodel.DenemeKind
import com.example.viewmodel.DenemePlan
import com.example.viewmodel.DenemePlanner
import com.example.viewmodel.DenemeRequest
import org.json.JSONObject
import java.util.Locale

private val navy = Color(0xFF211C59)
private val cobalt = Color(0xFF356CF3)
private val pale = Color(0xFFF4F6FD)
private val ink = Color(0xFF25335A)
private val grey = Color(0xFF66748C)
private val success = Color(0xFF13914D)

@Composable
fun DenemeStudioScreen(
    selectedFilter: ExamType?,
    mockExams: List<MockExamItem>,
    solvedRecords: List<TestResultRecord>,
    customQuestions: List<CustomQuestionRecord>,
    onSelectFilter: (ExamType?) -> Unit,
    onCreateDeneme: (DenemeRequest) -> Unit,
    onStartExam: (MockExamItem) -> Unit,
    onReviewExam: (TestResultRecord) -> Unit,
    onOpenPdfStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCreating by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<TestResultRecord?>(null) }
    val histories = remember(solvedRecords) {
        solvedRecords.filter { it.isSolvedAttempt && it.testId.startsWith("deneme_v147_") }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }
    val allExams = remember(mockExams) { mockExams.distinctBy { it.id }.sortedByDescending { it.id } }
    val listed = remember(selectedFilter, allExams) {
        allExams.filter { selectedFilter == null || it.examType == selectedFilter }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color.White)
            .testTag("denemeler_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(25.dp), color = navy,
                modifier = Modifier.fillMaxWidth().testTag("deneme_studio_hero")
            ) {
                Column(Modifier.background(Brush.linearGradient(listOf(navy, Color(0xFF3156BA))))
                    .padding(21.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DENEME STÜDYOSU", color = Color(0xFFB7D2FF),
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Gerçek sınav düzeni.\nSana özel denemeler.",
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp, lineHeight = 29.sp)
                    Text("ÖSYM branş kotaları • Dengeli konu seçimi • Doğrulanmış sorular",
                        fontSize = 12.sp, color = Color(0xFFE0E9FF))
                    Button(onClick = { isCreating = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = navy),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("create_deneme_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yeni Deneme Oluştur", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExamNumberCard("Oluşturulan",allExams.size.toString(),Modifier.weight(1f),cobalt)
                ExamNumberCard("Çözülen",histories.size.toString(),Modifier.weight(1f),success)
                val latest = histories.firstOrNull()?.netScore
                ExamNumberCard("Son net",latest?.let { String.format(Locale.forLanguageTag("tr-TR"),"%.2f",it) } ?: "—",
                    Modifier.weight(1f),Color(0xFFEA8D3A))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Denemelerim",fontSize = 20.sp,fontWeight = FontWeight.ExtraBold,color = navy)
                TextButton(onClick = onOpenPdfStudio) { Text("Soru kitaplığım",color = cobalt,fontSize = 12.sp) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null,ExamType.TYT,ExamType.AYT).forEach { choice ->
                    ChoiceChip(
                        text = choice?.name ?: "Tümü",
                        selected = choice == selectedFilter,
                        onClick = { onSelectFilter(choice) }
                    )
                }
            }
        }
        if (listed.isEmpty()) {
            item {
                Surface(color = pale,shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("İlk denemeni oluşturmaya hazır mısın?", fontWeight = FontWeight.Bold,color = navy)
                        Text("Branş ya da konu seç, zorluğu belirle. Sadece doğrulanmış soru havuzundan gerçek bir deneme oluşturulur.",
                            fontSize = 13.sp, color = grey)
                        Text(customQuestions.size.toString() + " kayıtlı soru • Kaynak eksikse sistem sana bildirecek",
                            fontSize = 11.sp,color = cobalt)
                    }
                }
            }
        } else {
            items(listed,key={it.id}) { exam ->
                val attempts = histories.filter { it.testId == exam.linkedTest.id }
                val createdAt = exam.id.substringAfterLast("_").toLongOrNull()?.takeIf { it > 1_000_000_000_000L }
                val last = attempts.firstOrNull()
                Surface(
                    color = Color.White, shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp,Color(0xFFE1E7F2)),
                    modifier = Modifier.fillMaxWidth().testTag("deneme_card_" + exam.id)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    ColorTag(exam.examType.name,cobalt)
                                    ColorTag(exam.difficulty.label,Color(0xFF8A5CF6))
                                }
                                Text(exam.title,color = navy,fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(exam.questionCount.toString() + " soru • " + exam.durationMinutes +
                                    " dk • " + exam.tag,fontSize = 11.sp,color = grey)
                            }
                            if (last != null) {
                                Text(String.format(Locale.forLanguageTag("tr-TR"),"%.2f net",last.netScore),
                                    color = success,fontWeight = FontWeight.ExtraBold,fontSize = 15.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF0F2F7))
                        createdAt?.let {
                            Text(java.text.SimpleDateFormat("d MMM yyyy • HH:mm",Locale.forLanguageTag("tr-TR"))
                                .apply { timeZone=java.util.TimeZone.getTimeZone("Europe/Istanbul") }.format(java.util.Date(it)),
                                color=grey,fontSize=11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            if(last != null) {
                                TextButton(onClick = { selectedRecord = last }) {
                                    Text("Karnem",color = cobalt,fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onStartExam(exam) },
                                colors = ButtonDefaults.buttonColors(containerColor = cobalt),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null,modifier=Modifier.size(17.dp))
                                Text(if(last != null) "Tekrar Çöz" else "Başla",fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (histories.isNotEmpty()) {
            item { Text("Deneme Geçmişim",fontSize = 20.sp,fontWeight = FontWeight.ExtraBold,color = navy) }
            items(histories.take(30),key={it.id}) { record ->
                Surface(onClick = { selectedRecord=record },
                    color = pale,shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().testTag("deneme_result_" + record.id)) {
                    Row(Modifier.padding(14.dp),verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(record.testTitle,color = navy,fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,maxLines = 2)
                            Text(record.correctCount.toString()+"D • "+record.wrongCount.toString()+"Y • "+
                                record.emptyCount.toString()+"B • "+
                                (record.durationSeconds/60).toString()+" dk",
                                color = grey,fontSize = 11.sp)
                        }
                        Text(String.format(Locale.forLanguageTag("tr-TR"),"%.2f",record.netScore),
                            color = cobalt,fontSize = 19.sp,fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward,contentDescription="Karneni aç",
                            modifier=Modifier.size(16.dp),tint = cobalt)
                    }
                }
            }
        }
    }
    if(isCreating) {
        DenemeCreatorSheet(
            questions = customQuestions,
            onDismiss = { isCreating=false },
            onCreate = { request -> isCreating=false;onCreateDeneme(request) }
        )
    }
    selectedRecord?.let { record ->
        DenemeResultSheet(record,
            onDismiss = { selectedRecord = null },
            onReview = { selectedRecord = null;onReviewExam(record) })
    }
}

@Composable
private fun ExamNumberCard(label:String,value:String,modifier:Modifier,color:Color) {
    Surface(modifier=modifier,color = pale,shape=RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value,fontSize=21.sp,color=color,fontWeight=FontWeight.ExtraBold)
            Text(label,fontSize=10.sp,color=grey)
        }
    }
}
@Composable
private fun ColorTag(label:String,color:Color) {
    Surface(shape=RoundedCornerShape(7.dp),color=color.copy(alpha=.12f)) {
        Text(label,Modifier.padding(horizontal=8.dp,vertical=4.dp),
            fontSize=10.sp,fontWeight=FontWeight.Bold,color=color)
    }
}
@Composable
private fun ChoiceChip(text:String,selected:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(11.dp),
        color=if(selected)cobalt else pale,
        modifier=Modifier.heightIn(min=38.dp)) {
        Box(Modifier.padding(horizontal=15.dp, vertical=9.dp),contentAlignment=Alignment.Center) {
            Text(text,fontWeight=FontWeight.SemiBold,fontSize=12.sp,
                color=if(selected)Color.White else ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DenemeCreatorSheet(
    questions: List<CustomQuestionRecord>,
    onDismiss:()->Unit,
    onCreate:(DenemeRequest)->Unit
) {
    var kind by remember { mutableStateOf(DenemeKind.BRANS) }
    var examType by remember { mutableStateOf(ExamType.TYT) }
    var blueprintId by remember { mutableStateOf("tyt_turkce") }
    var subjectId by remember { mutableStateOf("tyt_turkce") }
    var topicId by remember { mutableStateOf("sozcukte_anlam") }
    var difficulty by remember { mutableStateOf(DenemeDifficulty.DENGELI) }
    var count by remember { mutableIntStateOf(12) }
    var duration by remember { mutableIntStateOf(0) }
    var showDistribution by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var topicMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val choices = remember(examType) { DenemeBlueprints.forType(examType) }
    val subjects = remember(examType) { SoruLabCatalog.allSubjects.filter { it.examType == examType } }
    val chosenSubject = subjects.firstOrNull { it.id == subjectId } ?: subjects.first()
    val chosenTopic = chosenSubject.topics.firstOrNull { it.id == topicId } ?: chosenSubject.topics.first()
    val blueprint = choices.firstOrNull { it.id == blueprintId } ?: choices.first()
    val request = DenemeRequest(kind,examType,blueprint.id,chosenSubject.id,chosenTopic.id,count,difficulty,duration)
    val preview by key(request, questions) {
        produceState<DenemePlan?>(initialValue = null) {
            value = withContext(Dispatchers.Default) { DenemePlanner.build(request,questions,sequence=0L) }
        }
    }
    val quota = if (kind == DenemeKind.BRANS) blueprint.questionCount else count
    ModalBottomSheet(onDismissRequest=onDismiss,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Color.White,dragHandle={ BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()
            .heightIn(max=660.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Column {
                    Text("Deneme Oluştur",color=navy,fontWeight=FontWeight.ExtraBold,fontSize=22.sp)
                    Text("Konu planıyla • Kişisel soru kitaplığından",color=grey,fontSize=12.sp)
                }
                IconButton(onClick=onDismiss) { Icon(Icons.Default.Close,contentDescription="Kapat") }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(
                androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Deneme türü",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Branş Denemesi",kind==DenemeKind.BRANS,{ kind=DenemeKind.BRANS })
                    ChoiceChip("Konu Denemesi",kind==DenemeKind.KONU,{ kind=DenemeKind.KONU })
                }
                Text("Oturum",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ExamType.entries.forEach { type ->
                        ChoiceChip(type.name,examType==type) {
                            examType=type
                            blueprintId=DenemeBlueprints.forType(type).first().id
                            val first=SoruLabCatalog.allSubjects.first { it.examType==type }
                            subjectId=first.id
                            topicId=first.topics.first().id
                            duration=0
                        }
                    }
                }
                if(kind==DenemeKind.BRANS) {
                    Text("Branş seçimi",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick={ branchMenu=true },
                            modifier=Modifier.fillMaxWidth().testTag("deneme_branch_selector"),
                            shape=RoundedCornerShape(12.dp)) {
                            Text(blueprint.label,Modifier.weight(1f),color=navy)
                            Text(blueprint.questionCount.toString()+" soru ▾",color=cobalt,fontSize=12.sp)
                        }
                        DropdownMenu(expanded=branchMenu,onDismissRequest={branchMenu=false},
                            modifier=Modifier.heightIn(max=360.dp)) {
                            choices.forEach { value ->
                                DropdownMenuItem(text={ Text(value.label+" • "+value.questionCount+" soru") },
                                    onClick={blueprintId=value.id;duration=0;branchMenu=false})
                            }
                        }
                    }
                    Text("Kota: "+blueprint.sections.joinToString(" • "){ it.label+" "+it.count },
                        color=grey,fontSize=11.sp)
                } else {
                    Text("Ders",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(subjects,key={it.id}) { subject ->
                            ChoiceChip(subject.name,chosenSubject.id==subject.id) {
                                subjectId=subject.id
                                topicId=subject.topics.first().id
                                duration=0
                            }
                        }
                    }
                    Text("Konu",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick={topicMenu=true},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(12.dp)) {
                            Text(chosenTopic.name,Modifier.weight(1f),color=navy)
                            Text("▾",color=cobalt)
                        }
                        DropdownMenu(expanded=topicMenu,onDismissRequest={topicMenu=false},
                            modifier=Modifier.heightIn(max=360.dp)) {
                            chosenSubject.topics.forEach { topic ->
                                DropdownMenuItem(text={Text(topic.name)},
                                    onClick={topicId=topic.id;topicMenu=false})
                            }
                        }
                    }
                    Text("Soru sayısı",color=navy,fontWeight=FontWeight.Bold,fontSize=13.sp)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        items(listOf(5,7,8,10,12,15,16,20,30,40)) { value ->
                            ChoiceChip(value.toString(), count==value) {count=value;duration=0}
                        }
                    }
                }
                if (kind == DenemeKind.BRANS) {
                    Surface(color = pale, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            TextButton(onClick = { showDistribution = !showDistribution },
                                contentPadding = PaddingValues(0.dp)) {
                                Text(if(showDistribution) "Konu dağılımını gizle" else "Konu dağılımını gör",
                                    fontWeight = FontWeight.Bold, color = cobalt)
                            }
                            if (showDistribution) {
                                blueprint.sections.forEach { section ->
                                    Text(section.label, color = navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    DenemeTopicProfiles.forSection(section).forEach { quota ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(quota.label, Modifier.weight(1f), color = grey, fontSize = 12.sp)
                                            val missing = (preview as? DenemePlan.Missing)?.shortage?.firstOrNull {
                                                it.section == section.label + " • " + quota.label
                                            }
                                            Text(if(missing == null) quota.count.toString() + " soru"
                                                else missing.available.toString() + "/" + quota.count + " uygun",
                                                color = if(missing == null) navy else Color(0xFFAE5F12), fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    TextButton(onClick = { uriHandler.openUri(DenemeTopicProfiles.sourceFor(section)) },
                                        contentPadding = PaddingValues(0.dp)) {
                                        Text("Geçmiş yılların dağılımı ↗", color = cobalt, fontSize = 11.sp)
                                    }
                                }
                                Text(DenemeTopicProfiles.NOTICE, color = grey, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text("Zorluk",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                LazyRow(modifier=Modifier.testTag("deneme_difficulty_row"),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    items(DenemeDifficulty.entries) { d ->
                        ChoiceChip(d.label,difficulty==d) { difficulty=d }
                    }
                }
                Text("Süre • "+(duration.takeIf{it>0} ?: (
                    if(kind==DenemeKind.BRANS)blueprint.minutes else (quota*1.8f).toInt().coerceIn(5,240)
                )).toString()+" dakika",color=navy,fontSize=13.sp,fontWeight=FontWeight.Bold)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    items(listOf(0,20,30,40,50,60,75,90,120,165)) { value ->
                        ChoiceChip(if(value==0)"Önerilen" else value.toString()+" dk",
                            duration==value) {duration=value}
                    }
                }
            }
            Surface(color=if(preview is DenemePlan.Ready)Color(0xFFEAF9F1) else Color(0xFFFFF6E6),
                shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    val current = preview
                    if(current == null) {
                        Text("Soru kitaplığın kontrol ediliyor…", color = grey, fontSize = 12.sp)
                    } else if(current is DenemePlan.Ready) {
                        Text("Hazır • "+quota+" doğrulanmış soru",
                            color=success,fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(current.sectionSummary.joinToString(" • "){ it.first+": "+it.second },
                            color=grey,fontSize=11.sp)
                    } else if(current is DenemePlan.Missing) {
                        Text("Soru havuzunda eksikler var",color=Color(0xFFAE5F12),
                            fontSize=13.sp,fontWeight=FontWeight.Bold)
                        if(current.shortage.size > 4) Text("+" + (current.shortage.size-4) + " konuda daha eksik var. Konu dağılımını kontrol et.",color=grey,fontSize=11.sp)
                        current.shortage.take(4).forEach {
                            Text(it.section+": "+it.available+"/"+it.needed+" uygun soru",
                                color=Color(0xFF9A5B18),fontSize=11.sp)
                        }
                    }
                }
            }
            Button(
                onClick={onCreate(request)},
                enabled=preview is DenemePlan.Ready,
                colors=ButtonDefaults.buttonColors(containerColor=cobalt),
                modifier=Modifier.fillMaxWidth().height(51.dp)
                    .testTag("deneme_confirm_create"),
                shape=RoundedCornerShape(14.dp)
            ) {
                Text("Denemeyi Oluştur ve Başlat",fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(7.dp))
                Icon(Icons.Default.ArrowForward,contentDescription=null,modifier=Modifier.size(17.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private data class CourseScore(val name:String,val correct:Int,val wrong:Int,val blank:Int) {
    val net:Double get()=correct-wrong*.25
}
private fun courseScoreBreakdown(record:TestResultRecord): Pair<List<CourseScore>,List<Pair<String,Double>>> {
    val snapshot = TestSnapshotCodec.decode(record.testSnapshotJson) ?: return emptyList<CourseScore>() to emptyList()
    val answers=runCatching{JSONObject(record.answersMapJson)}.getOrNull() ?: JSONObject()
    val groups=snapshot.questions.groupBy { it.subject.ifBlank { "Ders" } }
    val courses=groups.map { (name,questions) ->
        var d=0;var y=0;var b=0
        questions.forEach { q ->
            val marked=answers.optString(q.number.toString())
            if(marked.isEmpty())b++
            else if(marked==q.correctOption)d++
            else y++
        }
        CourseScore(name,d,y,b)
    }
    val topicLoss=snapshot.questions.groupBy { it.subject + " • " + it.topicHeader.ifBlank { "Konu" } }
        .map { (topic, questions) -> topic to questions.sumOf { q ->
            val answer = answers.optString(q.number.toString())
            when { answer.isEmpty() -> 1.0; answer == q.correctOption -> 0.0; else -> 1.25 }
        } }.filter { it.second > 0.0 }.sortedByDescending { it.second }.take(6)
    return courses to topicLoss
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DenemeResultSheet(
    record:TestResultRecord,
    onDismiss:()->Unit,
    onReview:()->Unit
) {
    val breakdown=remember(record) { courseScoreBreakdown(record) }
    val snapshot=remember(record.testSnapshotJson) { TestSnapshotCodec.decode(record.testSnapshotJson) }
    ModalBottomSheet(onDismissRequest=onDismiss,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()
            .heightIn(max=630.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Text("Deneme Karnem",color=navy,fontSize=23.sp,fontWeight=FontWeight.ExtraBold)
            Text(record.testTitle,color=grey,fontSize=13.sp)
            Surface(shape=RoundedCornerShape(19.dp),color=navy,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(String.format(Locale.forLanguageTag("tr-TR"),"%.2f NET",record.netScore),
                        fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                    Text(record.correctCount.toString()+" Doğru • "+record.wrongCount.toString()+
                        " Yanlış • "+record.emptyCount.toString()+" Boş",
                        fontSize=13.sp,color=Color(0xFFCEDCFB))
                    Text((record.durationSeconds/60).toString()+" dakika • "+record.totalQuestions+
                        " soru",fontSize=11.sp,color=Color(0xFFB9C8F4))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(11.dp)) {
                snapshot?.takeIf { it.denemeProfileVersion.isNotBlank() }?.let { saved ->
                    Text(saved.denemeDifficultyLabel + " • Plan " + saved.denemeProfileVersion,
                        color = cobalt, fontSize = 11.sp)
                    Text(if (saved.denemeReusedQuestionCount > 0)
                        saved.denemeReusedQuestionCount.toString() + " soru son 6 denemende de vardı."
                        else "Son 6 denemenden tekrar soru kullanılmadı.", color = grey, fontSize = 11.sp)
                    Text("Uygulanan konu planı",color=navy,fontWeight=FontWeight.Bold,fontSize=14.sp)
                    Text(saved.denemeDistributionSummary,color=grey,fontSize=12.sp)
                }
                Text("Ders Bazlı Sonuçlar",color=navy,fontWeight=FontWeight.Bold,fontSize=16.sp)
                breakdown.first.forEach { course ->
                    Surface(color=pale,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(course.name,color=navy,fontWeight=FontWeight.Bold,fontSize=13.sp)
                                Text(String.format(Locale.forLanguageTag("tr-TR"),"%.2f net",course.net),
                                    color=cobalt,fontWeight=FontWeight.Bold,fontSize=13.sp)
                            }
                            Text(course.correct.toString()+"D • "+course.wrong.toString()+"Y • "+
                                course.blank.toString()+"B",fontSize=11.sp,color=grey)
                            LinearProgressIndicator(
                                progress={if(course.correct+course.wrong+course.blank>0)
                                    course.correct.toFloat()/(course.correct+course.wrong+course.blank) else 0f},
                                color=success,trackColor=Color(0xFFE0E5F2),
                                modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                            )
                        }
                    }
                }
                Text("En Çok Net Kaybettiren Konular",color=navy,fontWeight=FontWeight.Bold,fontSize=15.sp)
                if(breakdown.second.isEmpty()) {
                    Text("Bu denemede konu kaybı bulunmadı.",color=grey,fontSize=12.sp)
                }
                breakdown.second.forEach { (topic,loss) ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(topic,color=grey,fontSize=12.sp,modifier=Modifier.weight(1f))
                        Text(String.format(Locale.forLanguageTag("tr-TR"),"%.2f net",loss),color=Color(0xFFD4653A),
                            fontWeight=FontWeight.Bold,fontSize=12.sp)
                    }
                }
            }
            Button(onClick=onReview,modifier=Modifier.fillMaxWidth().height(48.dp),
                shape=RoundedCornerShape(13.dp),
                colors=ButtonDefaults.buttonColors(containerColor=cobalt)) {
                Text("Sorularımı İncele",fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
