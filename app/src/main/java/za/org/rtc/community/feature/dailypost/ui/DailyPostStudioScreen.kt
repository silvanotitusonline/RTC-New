package za.org.rtc.community.feature.dailypost.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import za.org.rtc.community.feature.dailypost.domain.DailyPostArticle
import za.org.rtc.community.feature.dailypost.domain.DailyPostPresets
import za.org.rtc.community.feature.dailypost.domain.DailyPostTemplateStyle
import za.org.rtc.community.feature.dailypost.domain.calculateEstimatedReadingTimeMinutes
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyPostStudioScreen(
    viewModel: DailyPostViewModel,
    onBack: () -> Unit,
    onOpenPublishedPost: (DailyPostArticle) -> Unit = {},
    adminAuthorName: String = "Office of the Administrator",
    adminRoleName: String = "System Administrator",
) {
    val allArticles by viewModel.allArticles.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissStatusMessage()
        }
    }

    var selectedStudioTab by remember { mutableIntStateOf(0) } // 0: Editor & Preview, 1: Published Articles Library

    // Form states
    var editingArticleId by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf(DailyPostTemplateStyle.MODERN_BLOG) }
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DailyPostPresets.Categories.first()) }
    var selectedColorHex by remember { mutableStateOf(DailyPostPresets.ColorPalettes.first().hex) }
    var authorName by remember { mutableStateOf(adminAuthorName) }
    var authorRole by remember { mutableStateOf(adminRoleName) }
    var quoteText by remember { mutableStateOf("") }
    var quoteAuthor by remember { mutableStateOf("") }
    var pushEnabled by remember { mutableStateOf(false) }
    var previewPopupEnabled by remember { mutableStateOf(true) }
    val highlights = remember { mutableStateListOf<String>() }
    var newHighlightInput by remember { mutableStateOf("") }
    // Dynamic calculation of estimated reading time
    val computedReadTimeMinutes = remember(title, subtitle, content, highlights.toList()) {
        calculateEstimatedReadingTimeMinutes(title, subtitle, content, highlights.toList())
    }

    // Live mode switch: 0 = Form Editor, 1 = Canva/Blog Live Preview
    var editorMode by remember { mutableIntStateOf(0) }

    fun loadArticleForEditing(article: DailyPostArticle) {
        editingArticleId = article.id
        selectedTemplate = article.templateStyle
        title = article.title
        subtitle = article.subtitle
        content = article.content
        selectedCategory = article.category
        selectedColorHex = article.accentColorHex
        authorName = article.authorName
        authorRole = article.authorRole
        quoteText = article.quoteText.orEmpty()
        quoteAuthor = article.quoteAuthor.orEmpty()
        pushEnabled = article.pushEnabled
        previewPopupEnabled = article.previewPopupEnabled
        highlights.clear()
        highlights.addAll(article.keyHighlights)
        selectedStudioTab = 0
        editorMode = 0
    }

    fun resetForm() {
        editingArticleId = null
        selectedTemplate = DailyPostTemplateStyle.MODERN_BLOG
        title = ""
        subtitle = ""
        content = ""
        selectedCategory = DailyPostPresets.Categories.first()
        selectedColorHex = DailyPostPresets.ColorPalettes.first().hex
        authorName = adminAuthorName
        authorRole = adminRoleName
        quoteText = ""
        quoteAuthor = ""
        pushEnabled = false
        previewPopupEnabled = true
        highlights.clear()
        editorMode = 0
    }

    // Build the preview article object
    val currentArticlePreview = remember(
        editingArticleId, title, subtitle, content, selectedCategory,
        selectedColorHex, selectedTemplate, authorName, authorRole,
        quoteText, quoteAuthor, highlights.toList(), computedReadTimeMinutes
    ) {
        DailyPostArticle(
            id = editingArticleId ?: UUID.randomUUID().toString(),
            title = if (title.isBlank()) "Official Administrative Update" else title,
            subtitle = subtitle,
            content = if (content.isBlank()) "Administrators share official news, updates, civic announcements, and community guidelines here." else content,
            category = selectedCategory,
            authorName = if (authorName.isBlank()) adminAuthorName else authorName,
            authorRole = if (authorRole.isBlank()) adminRoleName else authorRole,
            templateStyle = selectedTemplate,
            accentColorHex = selectedColorHex,
            keyHighlights = highlights.toList(),
            quoteText = quoteText.takeIf { it.isNotBlank() },
            quoteAuthor = quoteAuthor.takeIf { it.isNotBlank() },
            publishedAtEpochMillis = System.currentTimeMillis(),
            readTimeMinutes = computedReadTimeMinutes,
            reactionsCount = 0,
            viewerHasLiked = false,
            isPublished = true,
            pushEnabled = pushEnabled,
            previewPopupEnabled = previewPopupEnabled,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Daily Post Studio", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Canva & Blog Template Publishing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedStudioTab == 0 && editorMode == 0) {
                        Button(
                            onClick = { editorMode = 1 },
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview")
                        }
                    } else if (selectedStudioTab == 0 && editorMode == 1) {
                        Button(
                            onClick = { editorMode = 0 },
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Form")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedStudioTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedStudioTab == 0,
                    onClick = { selectedStudioTab = 0 },
                    text = { Text(if (editingArticleId != null) "Edit Article" else "Create Post", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedStudioTab == 1,
                    onClick = { selectedStudioTab = 1 },
                    text = { Text("Articles Library (${allArticles.size})", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedStudioTab == 0) {
                // Top sub-header with Mode selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editorMode == 0) "MODE: COMPOSE & TEMPLATES" else "MODE: LIVE CANVA/BLOG PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editorMode == 0,
                            onClick = { editorMode = 0 },
                            label = { Text("Editor") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = editorMode == 1,
                            onClick = { editorMode = 1 },
                            label = { Text("Live Preview") },
                            leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                if (editorMode == 0) {
                    // EDIT FORM
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. TEMPLATE SELECTOR CAROUSEL
                        Text("1. CHOOSE CANVA / BLOG TEMPLATE", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(DailyPostTemplateStyle.entries, key = { it.name }) { template ->
                                val isSelected = selectedTemplate == template
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedTemplate = template },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when (template) {
                                                    DailyPostTemplateStyle.MODERN_BLOG -> Icons.Filled.Assignment
                                                    DailyPostTemplateStyle.CANVA_HERO -> Icons.Filled.Palette
                                                    DailyPostTemplateStyle.CIVIC_SPOTLIGHT -> Icons.Filled.Shield
                                                    DailyPostTemplateStyle.MAGAZINE_STORY -> Icons.Filled.AutoStories
                                                    DailyPostTemplateStyle.BREAKING_BULLETIN -> Icons.Filled.Campaign
                                                    DailyPostTemplateStyle.MINIMALIST_EDITORIAL -> Icons.Filled.MenuBook
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isSelected) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Text(
                                            text = template.displayName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = template.description,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }

                        // 2. ACCENT COLOR PALETTE
                        Text("2. ACCENT COLOR THEME", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(DailyPostPresets.ColorPalettes, key = { it.hex }) { palette ->
                                val isSelected = selectedColorHex == palette.hex
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = parseColorSafe(palette.hex),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable { selectedColorHex = palette.hex }
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // 3. ARTICLE DETAILS
                        Text("3. POST CONTENT & HEADLINES", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))

                        // Category Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DailyPostPresets.Categories.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Article Headline *") },
                            placeholder = { Text("e.g. Community Clean-Up & Infrastructure Briefing") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = subtitle,
                            onValueChange = { subtitle = it },
                            label = { Text("Subtitle / Summary Tagline") },
                            placeholder = { Text("e.g. Key progress updates and municipal schedules for September") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Full Article Content *") },
                            placeholder = { Text("Write the detailed announcement, article body, or blog post here...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val wordCount = remember(title, subtitle, content) {
                                "$title $subtitle $content".split(Regex("\\s+")).count { it.isNotBlank() }
                            }
                            Text(
                                text = "$wordCount words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "⏱️ Estimated Read: $computedReadTimeMinutes min read",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // 4. PULL QUOTE (OPTIONAL)
                        Text("4. PULL QUOTE CALLOUT (OPTIONAL)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                        OutlinedTextField(
                            value = quoteText,
                            onValueChange = { quoteText = it },
                            label = { Text("Featured Quote") },
                            placeholder = { Text("e.g. Together we build a more connected and responsive community.") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = quoteAuthor,
                            onValueChange = { quoteAuthor = it },
                            label = { Text("Quote Attribution") },
                            placeholder = { Text("e.g. Executive Councillor for Civic Services") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 5. KEY TAKEAWAYS / BULLET POINTS
                        Text("5. KEY TAKEAWAYS & BULLETS", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newHighlightInput,
                                onValueChange = { newHighlightInput = it },
                                placeholder = { Text("Add bullet highlight...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (newHighlightInput.isNotBlank()) {
                                        highlights.add(newHighlightInput.trim())
                                        newHighlightInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                            }
                        }

                        if (highlights.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                highlights.forEachIndexed { index, item ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = parseColorSafe(selectedColorHex), modifier = Modifier.size(16.dp))
                                                Text(item, style = MaterialTheme.typography.bodySmall)
                                            }
                                            IconButton(onClick = { highlights.removeAt(index) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Author byline fields
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = authorName,
                                onValueChange = { authorName = it },
                                label = { Text("Author Name") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = authorRole,
                                onValueChange = { authorRole = it },
                                label = { Text("Author Designation") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Text("6. RESIDENT NOTIFICATION", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Push notification", fontWeight = FontWeight.Bold)
                                        Text("Queue the official headline and summary for resident delivery after publication.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = pushEnabled, onCheckedChange = { pushEnabled = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("In-app preview popup", fontWeight = FontWeight.Bold)
                                        Text("Show the new article preview in the resident feed when available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = previewPopupEnabled, onCheckedChange = { previewPopupEnabled = it })
                                }
                                if (pushEnabled) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("NOTIFICATION PREVIEW", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
                                            Text(if (title.isBlank()) "Article headline" else title, fontWeight = FontWeight.Bold, maxLines = 2)
                                            Text((if (subtitle.isBlank()) content else subtitle).ifBlank { "Add a summary to preview the notification." }, style = MaterialTheme.typography.bodySmall, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ACTION BUTTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveDraft(currentArticlePreview) {
                                        scope.launch { snackbarHostState.showSnackbar("Draft saved successfully.") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Draft")
                            }

                            Button(
                                onClick = {
                                    if (title.isBlank() || content.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("Please provide a title and article content before publishing.") }
                                    } else {
                                        viewModel.publishArticle(currentArticlePreview) {
                                            onOpenPublishedPost(currentArticlePreview)
                                            resetForm()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Publish Post")
                            }
                        }
                    }
                } else {
                    // LIVE WYSIWYG PREVIEW MODE
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("WYSIWYG TEMPLATE PREVIEW", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    Text("Template: ${selectedTemplate.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Button(
                                    onClick = {
                                        if (title.isBlank() || content.isBlank()) {
                                            scope.launch { snackbarHostState.showSnackbar("Add a headline and article content before publishing.") }
                                        } else {
                                            viewModel.publishArticle(currentArticlePreview) {
                                                onOpenPublishedPost(currentArticlePreview)
                                                resetForm()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Publish Now")
                                }
                            }
                        }

                        // RENDER LIVE CARD IN CHOSEN TEMPLATE
                        Text("FEED CARD PREVIEW", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        DailyPostTemplateCard(
                            article = currentArticlePreview,
                            onClick = {},
                            isPreview = true
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text("FULL ARTICLE READER PREVIEW", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = currentArticlePreview.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                                )
                                if (currentArticlePreview.subtitle.isNotBlank()) {
                                    Text(
                                        text = currentArticlePreview.subtitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                HorizontalDivider()
                                Text(
                                    text = currentArticlePreview.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ARTICLES LIBRARY (MANAGE PREVIOUSLY POSTED ARTICLES)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Published Daily Posts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = {
                                resetForm()
                                selectedStudioTab = 0
                                editorMode = 0
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Post")
                        }
                    }

                    if (allArticles.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No Daily Posts Yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Create your first article with Canva/Blog templates to publish updates to the Daily Post menu.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    } else {
                        allArticles.forEach { article ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = parseColorSafe(article.accentColorHex).copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "${article.templateStyle.displayName} • ${article.category}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = parseColorSafe(article.accentColorHex),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        Text(
                                            text = if (article.isPublished) "PUBLISHED" else "DRAFT",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (article.isPublished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Text(
                                        text = article.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatPublishedDate(article.publishedAtEpochMillis)} • ${article.reactionsCount} likes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = { loadArticleForEditing(article) }) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { viewModel.deleteArticle(article.id) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
