package za.org.rtc.community.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import za.org.rtc.community.navigation.RtcRoute

private data class OnboardingCopy(
    val step: String,
    val skip: String,
    val previous: String,
    val next: String,
    val finish: String,
    val chooseLanguage: String,
    val communityTitle: String,
    val communityBody: String,
    val communityAction: String,
    val marketTitle: String,
    val marketBody: String,
    val marketAction: String,
    val exploreTitle: String,
    val exploreBody: String,
    val exploreAction: String,
)

private val onboardingCopy = mapOf(
    "en" to OnboardingCopy(
        "Step", "Skip tutorial", "Previous", "Next", "Get started", "Language",
        "Community Snapshot", "See verified community issues, active work and resolved reports at a glance.", "Open Home",
        "Local Market", "Discover nearby businesses, services and opportunities built around your community.", "Open Market",
        "Explore your community", "Read The Daily Post for official and breaking stories, then switch to Community Updates for notices, projects, opportunities and the public-report map.", "Open Explore",
    ),
    "af" to OnboardingCopy(
        "Stap", "Slaan tutoriaal oor", "Vorige", "Volgende", "Begin", "Taal",
        "Gemeenskapsoorsig", "Sien geverifieerde gemeenskapskwessies, aktiewe werk en opgeloste verslae in een oogopslag.", "Open Tuis",
        "Plaaslike Mark", "Ontdek nabygeleë besighede, dienste en geleenthede in jou gemeenskap.", "Open Mark",
        "Verken jou gemeenskap", "Lees The Daily Post vir amptelike en dringende stories en gebruik Gemeenskapsopdaterings vir kennisgewings, projekte, geleenthede en die openbare verslagkaart.", "Open Verken",
    ),
    "zu" to OnboardingCopy(
        "Isinyathelo", "Yeqa ukufundiswa", "Emuva", "Okulandelayo", "Qala", "Ulimi",
        "Isifinyezo Somphakathi", "Bona izinkinga eziqinisekisiwe, umsebenzi oqhubekayo nemibiko esixazululiwe ngokushesha.", "Vula Ikhaya",
        "Imakethe Yasendaweni", "Thola amabhizinisi, izinsiza namathuba aseduze emphakathini wakho.", "Vula Imakethe",
        "Hlola umphakathi wakho", "Funda The Daily Post ngezindaba ezisemthethweni neziphuthumayo, bese usebenzisa Community Updates ukuthola izaziso, amaphrojekthi, amathuba nemephu yemibiko.", "Vula i-Explore",
    ),
    "xh" to OnboardingCopy(
        "Inyathelo", "Tsiba isikhokelo", "Emva", "Okulandelayo", "Qalisa", "Ulwimi",
        "Isishwankathelo Soluntu", "Bona iingxaki eziqinisekisiweyo, umsebenzi oqhubekayo kunye neengxelo ezisonjululweyo ngokukhawuleza.", "Vula Ikhaya",
        "Imarike Yasekuhlaleni", "Fumana amashishini, iinkonzo namathuba akufutshane kuluntu lwakho.", "Vula Imarike",
        "Phonononga uluntu lwakho", "Funda The Daily Post ngeendaba ezisemthethweni nezingxamisekileyo, uze usebenzise Community Updates kwizaziso, iiprojekthi, amathuba kunye nemephu yeengxelo.", "Vula i-Explore",
    ),
    "st" to OnboardingCopy(
        "Mohato", "Tlola thupelo", "Morao", "E latelang", "Qala", "Puo",
        "Kakaretso ea Sechaba", "Bona mathata a netefalitsoeng, mosebetsi o ntseng o tsoela pele le litlaleho tse rarollotsoeng habonolo.", "Bula Lehae",
        "Mmaraka oa Lehae", "Fumana likhoebo, litšebeletso le menyetla e haufi sechabeng sa heno.", "Bula Mmaraka",
        "Hlahloba sechaba sa heno", "Bala The Daily Post bakeng sa litaba tsa semmuso le tse potlakileng, ebe u sebelisa Community Updates bakeng sa ditsebiso, merero, menyetla le mmapa wa ditlaleho.", "Bula Explore",
    ),
    "tn" to OnboardingCopy(
        "Kgato", "Tlola thuto", "E e fetileng", "E e latelang", "Simolola", "Puo",
        "Tshobokanyo ya Setšhaba", "Bona dikgang tse di netefaditsweng, tiro e e tsweletseng le dipegelo tse di rarabolotsweng ka bonako.", "Bula Gae",
        "Mmaraka wa Selegae", "Bona dikgwebo, ditirelo le ditshono tse di gaufi mo setšhabeng sa gago.", "Bula Mmaraka",
        "Tlhotlhomisa setšhaba sa gago", "Bala The Daily Post bakeng sa dikgang tsa semmuso le tse di potlakileng, mme o dirise Community Updates bakeng sa dikitsiso, diporojeke, ditshono le mmapa wa dipegelo.", "Bula Explore",
    ),
)

private val languageChoices = listOf(
    "en" to "English",
    "af" to "Afrikaans",
    "zu" to "isiZulu",
    "xh" to "isiXhosa",
    "st" to "Sesotho",
    "tn" to "Setswana",
)

@Composable
fun InteractiveOnboardingTutorial(
    onDismiss: () -> Unit,
    onNavigateToFeature: (String) -> Unit,
    modifier: Modifier = Modifier,
    localizationViewModel: OnboardingLocalizationViewModel = hiltViewModel(),
) {
    val language by localizationViewModel.language.collectAsStateWithLifecycle()
    val copy = onboardingCopy[language] ?: requireNotNull(onboardingCopy["en"])
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 3

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).padding(16.dp).testTag("interactive_onboarding_tutorial"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text("${copy.step} ${currentStep + 1} / $totalSteps", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.testTag("onboarding_skip_button")) { Text(copy.skip) }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                            Text(copy.chooseLanguage, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        FlowLanguageSelector(language = language, onLanguage = localizationViewModel::setLanguage)

                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                }
                            },
                            label = "onboarding_step_transition",
                        ) { step ->
                            when (step) {
                                0 -> FeatureTutorialStep(Icons.Default.PieChart, copy.communityTitle, copy.communityBody, copy.communityAction, "onboarding_step_community_snapshot") { onNavigateToFeature(RtcRoute.HOME) }
                                1 -> FeatureTutorialStep(Icons.Default.Storefront, copy.marketTitle, copy.marketBody, copy.marketAction, "onboarding_step_marketplace") { onNavigateToFeature(RtcRoute.MARKETPLACE_HOME) }
                                else -> FeatureTutorialStep(Icons.Default.Explore, copy.exploreTitle, copy.exploreBody, copy.exploreAction, "onboarding_step_explore") { onNavigateToFeature(RtcRoute.EXPLORE) }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        repeat(totalSteps) { index ->
                            Box(
                                modifier = Modifier.padding(horizontal = 4.dp).size(width = if (index == currentStep) 24.dp else 8.dp, height = 8.dp)
                                    .background(if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (currentStep > 0) {
                            OutlinedButton(onClick = { currentStep-- }, modifier = Modifier.weight(1f).testTag("onboarding_prev_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(copy.previous)
                            }
                        }
                        if (currentStep < totalSteps - 1) {
                            Button(onClick = { currentStep++ }, modifier = Modifier.weight(1f).testTag("onboarding_next_button")) {
                                Text(copy.next); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Button(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("onboarding_finish_button")) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(copy.finish)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowLanguageSelector(language: String, onLanguage: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        languageChoices.forEach { (code, label) ->
            AssistChip(
                onClick = { onLanguage(code) },
                label = { Text(if (language == code) "✓ $label" else label) },
            )
        }
    }
}

@Composable
private fun FeatureTutorialStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    testTag: String,
    onJump: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, modifier = Modifier.padding(18.dp).size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onJump) { Text(action) }
    }
}
