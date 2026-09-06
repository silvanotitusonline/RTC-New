package za.org.rtc.community.app

import za.org.rtc.community.data.AdministratorTotpEnrollment

data class AuthenticationUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val confirmationRequired: Boolean = false,
    val isSuccess: Boolean = false,
)

data class AdministratorMfaUiState(
    val isWorking: Boolean = false,
    val enrollment: AdministratorTotpEnrollment? = null,
    val message: String? = null,
)

data class AiUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

data class PasswordUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

data class DeclaredLocalityUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

data class WorkflowSubmissionUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

enum class CommunityAction { GUIDELINES, POST, COMMENT, LIKE }

data class CommunityActionUiState(
    val action: CommunityAction? = null,
    val isWorking: Boolean = false,
    val isSuccess: Boolean = false,
    val message: String? = null,
)
