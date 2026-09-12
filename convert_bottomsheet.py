import re

with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "r") as f:
    content = f.read()

# Add ModalBottomSheet imports
import_statement = "import androidx.compose.material3.ModalBottomSheet\nimport androidx.compose.material3.rememberModalBottomSheetState\n"
import_idx = content.find("import androidx.compose.material3.*")
content = content[:import_idx] + import_statement + content[import_idx:]

# Find the var selectedTabIndex state and change it to showBottomSheet
content = content.replace("var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }", "var showBottomSheet by rememberSaveable { mutableStateOf(false) }\n    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)")

# Replace the TabRow block with the Timeline content, and add the ModalBottomSheet block
tabrow_start = content.find("                // Dual Tab Switcher: Timeline vs Chat")
tabrow_end = content.find("                when (selectedTabIndex) {")
when_end = content.find("                    1 -> BookingChatTabContent(")
chat_tab_end = content.find("                        isWorking = state.working,\n                    )\n                }") + len("                        isWorking = state.working,\n                    )\n                }")

# We will replace everything from tabrow_start to chat_tab_end with just the timeline, and add a bottom sheet if showBottomSheet is true.
new_content = """                BookingTimelineTabContent(
                    booking = booking,
                    viewModel = viewModel,
                    onSwitchToChat = { showBottomSheet = true },
                )
                
                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        BookingChatTabContent(
                            bookingId = booking.id,
                            messages = state.messages,
                            chatBody = chatBody,
                            onBodyChange = { chatBody = it },
                            onSendMessage = {
                                viewModel.sendMessage(booking.id, chatBody)
                                chatBody = ""
                            },
                            isWorking = state.working,
                        )
                    }
                }"""

content = content[:tabrow_start] + new_content + content[chat_tab_end:]

with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "w") as f:
    f.write(content)
