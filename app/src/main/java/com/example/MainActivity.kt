package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.EmergencyOrchestrator
import com.example.ui.components.EmergencyBanner
import com.example.ui.components.NaariKavachBottomNavBar
import com.example.ui.navigation.NavRoutes
import com.example.ui.screens.auth.SignInScreen
import com.example.ui.screens.contacts.TrustedContactsScreen
import com.example.ui.screens.child.ChildSafetyScreen
import com.example.ui.screens.emergency.EmergencyActiveScreen
import com.example.ui.screens.evidence.EvidenceVaultScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.journey.ActiveJourneyScreen
import com.example.ui.screens.map.NagpurSafetyMapScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.privacy.PrivacyCenterScreen
import com.example.ui.screens.privacy.BackendConnectionScreen
import com.example.ui.screens.threat.AnalysisResultScreen
import com.example.ui.screens.threat.IncidentReportScreen
import com.example.ui.screens.threat.ThreatAnalyzerScreen
import com.example.ui.screens.voice.VoiceSosEnrollmentScreen
import com.example.ui.screens.voice.VoiceSosSettingsScreen
import com.example.ui.theme.NaariKavachTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NaariKavachTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.HOME

    val activeEmergency by viewModel.activeEmergency.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val isAppTourActive by viewModel.isAppTourActive.collectAsState()
    val currentTourStep by viewModel.currentTourStep.collectAsState()
    val strings = viewModel.getStrings()

    // Protected Navigation Boundary: Redirect unauthenticated users to Sign In
    LaunchedEffect(authState) {
        if (authState is com.example.data.repository.AuthState.Unauthenticated) {
            if (currentRoute != NavRoutes.SIGN_IN && currentRoute != NavRoutes.ONBOARDING) {
                navController.navigate(NavRoutes.SIGN_IN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } else if (authState is com.example.data.repository.AuthState.Authenticated) {
            if (currentRoute == NavRoutes.SIGN_IN) {
                navController.navigate(NavRoutes.HOME) {
                    popUpTo(NavRoutes.SIGN_IN) { inclusive = true }
                }
            }
        }
    }

    // Request permissions on launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions evaluated
    }

    LaunchedEffect(Unit) {
        val permissionsToAsk = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToAsk.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToAsk.toTypedArray())
    }

    // React to automatic or manual emergency triggers by immediately navigating to Emergency Screen
    LaunchedEffect(Unit) {
        EmergencyOrchestrator.emergencyTriggeredEvents.collect {
            if (currentRoute != NavRoutes.EMERGENCY_ACTIVE) {
                navController.navigate(NavRoutes.EMERGENCY_ACTIVE) {
                    launchSingleTop = true
                }
            }
        }
    }

    // Screens that display the bottom navigation bar matching user's design
    val bottomNavRoutes = listOf(
        NavRoutes.HOME,
        NavRoutes.MAP,
        NavRoutes.JOURNEY,
        NavRoutes.ACTIVE_JOURNEY,
        NavRoutes.THREATS,
        NavRoutes.PRIVACY
    )
    val showBottomBar = currentRoute in bottomNavRoutes && activeEmergency == null && !isAppTourActive

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (activeEmergency != null && currentRoute != NavRoutes.EMERGENCY_ACTIVE) {
                            Modifier
                                .background(com.example.ui.theme.EmergencyRed)
                                .statusBarsPadding()
                        } else {
                            Modifier
                        }
                    )
            ) {
                // High-visibility Emergency Banner when active and not already on Emergency Screen
                if (activeEmergency != null && currentRoute != NavRoutes.EMERGENCY_ACTIVE) {
                    EmergencyBanner(
                        activeEmergency = activeEmergency!!,
                        onViewEmergencyClick = {
                            navController.navigate(NavRoutes.EMERGENCY_ACTIVE)
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NaariKavachBottomNavBar(
                    currentRoute = if (currentRoute == NavRoutes.ACTIVE_JOURNEY) NavRoutes.JOURNEY else currentRoute,
                    strings = strings,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(NavRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = if (viewModel.currentUser != null) NavRoutes.HOME else NavRoutes.SIGN_IN
            ) {
                // 0. Sign In Screen
                composable(NavRoutes.SIGN_IN) {
                    SignInScreen(
                        viewModel = viewModel,
                        onSignInSuccess = {
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.SIGN_IN) { inclusive = true }
                            }
                        }
                    )
                }

                // 1. Onboarding Screen
                composable(NavRoutes.ONBOARDING) {
                    OnboardingScreen(
                        onGetStarted = {
                            navController.navigate(NavRoutes.SIGN_IN) {
                                popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }

                // 2. Home Screen
                composable(NavRoutes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onNavigate = { target -> navController.navigate(target) }
                    )
                }

                // 3. Map Screen
                composable(NavRoutes.MAP) {
                    NagpurSafetyMapScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onStartJourneyClick = { navController.navigate(NavRoutes.ACTIVE_JOURNEY) }
                    )
                }

                // Child safety: local-first child profiles, safe-place geofences, check-ins, and pickup PINs.
                composable(NavRoutes.CHILD_SAFETY) {
                    ChildSafetyScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onTriggerChildSos = { alias -> viewModel.triggerChildSos(alias) }
                    )
                }

                // 4. Active Journey Screen
                composable(NavRoutes.JOURNEY) {
                    ActiveJourneyScreen(
                        viewModel = viewModel,
                        onBack = { navController.navigate(NavRoutes.HOME) },
                        onEndJourney = { navController.navigate(NavRoutes.HOME) },
                        onEmergencyClick = { viewModel.triggerManualPanic() }
                    )
                }
                composable(NavRoutes.ACTIVE_JOURNEY) {
                    ActiveJourneyScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onEndJourney = { navController.navigate(NavRoutes.HOME) },
                        onEmergencyClick = { viewModel.triggerManualPanic() }
                    )
                }

                // 5. Threat Analyzer Screen
                composable(NavRoutes.THREATS) {
                    ThreatAnalyzerScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() },
                        onViewResult = { navController.navigate(NavRoutes.ANALYSIS_RESULT) }
                    )
                }

                // 6. Threat Analysis Result Screen
                composable(NavRoutes.ANALYSIS_RESULT) {
                    AnalysisResultScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSaveAsIncident = { navController.navigate(NavRoutes.INCIDENT_REPORT) },
                        onCreateReport = { navController.navigate(NavRoutes.INCIDENT_REPORT) }
                    )
                }

                // 7. Incident Report Screen
                composable(NavRoutes.INCIDENT_REPORT) {
                    IncidentReportScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onSubmitSuccess = { navController.navigate(NavRoutes.HOME) }
                    )
                }

                // 8. Voice SOS Enrollment Screen
                composable(NavRoutes.VOICE_ENROLL) {
                    VoiceSosEnrollmentScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() },
                        onEnrollmentComplete = {
                            navController.navigate(NavRoutes.VOICE_SETTINGS) {
                                popUpTo(NavRoutes.HOME)
                            }
                        }
                    )
                }

                // 9. Voice SOS Dashboard Screen
                composable(NavRoutes.VOICE_SETTINGS) {
                    VoiceSosSettingsScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() },
                        onNavigate = { target -> navController.navigate(target) }
                    )
                }

                // 10. Emergency Activated Screen
                composable(NavRoutes.EMERGENCY_ACTIVE) {
                    EmergencyActiveScreen(
                        viewModel = viewModel,
                        strings = strings,
                        activeEmergency = activeEmergency,
                        onEmergencyEnded = {
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                // 11. Trusted Contacts Screen
                composable(NavRoutes.CONTACTS) {
                    TrustedContactsScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 12. Privacy Center Screen
                composable(NavRoutes.PRIVACY) {
                    PrivacyCenterScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() },
                        onNavigate = { target -> navController.navigate(target) }
                    )
                }

                composable(NavRoutes.BACKEND_SETTINGS) {
                    BackendConnectionScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Evidence Vault Screen
                composable(NavRoutes.EVIDENCE) {
                    EvidenceVaultScreen(
                        viewModel = viewModel,
                        strings = strings,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 13. ElevenLabs AI Assistant Screen
                composable(NavRoutes.ASSISTANT) {
                    com.example.assistant.AssistantScreen(
                        assistantManager = viewModel.assistantManager,
                        onBack = { navController.popBackStack() },
                        onNavigateTarget = { target ->
                            when (target) {
                                "HOME" -> navController.navigate(NavRoutes.HOME)
                                "MAP" -> navController.navigate(NavRoutes.MAP)
                                "SAFE_JOURNEY" -> navController.navigate(NavRoutes.JOURNEY)
                                "THREAT_ANALYZER" -> navController.navigate(NavRoutes.THREATS)
                                "CHILD_SAFETY" -> navController.navigate(NavRoutes.CHILD_SAFETY)
                                "EMERGENCY_REPORTING" -> navController.navigate(NavRoutes.INCIDENT_REPORT)
                                "PRIVACY_CENTER" -> navController.navigate(NavRoutes.PRIVACY)
                                "EMERGENCY_CONTACTS" -> navController.navigate(NavRoutes.CONTACTS)
                                else -> navController.navigate(NavRoutes.HOME)
                            }
                        }
                    )
                }
            }

            // Interactive 8-Step App Tour Overlay
            if (isAppTourActive && activeEmergency == null) {
                com.example.ui.components.InteractiveTourOverlay(
                    currentStep = currentTourStep,
                    onNext = {
                        val nextStep = currentTourStep + 1
                        if (nextStep <= com.example.ui.components.APP_TOUR_STEPS.size) {
                            val nextTargetRoute = com.example.ui.components.APP_TOUR_STEPS[nextStep - 1].targetRoute
                            navController.navigate(nextTargetRoute) {
                                launchSingleTop = true
                            }
                            viewModel.nextTourStep()
                        } else {
                            viewModel.dismissAppTour(markCompleted = true)
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.HOME) { inclusive = false }
                            }
                        }
                    },
                    onBack = {
                        val prevStep = currentTourStep - 1
                        if (prevStep >= 1) {
                            val prevTargetRoute = com.example.ui.components.APP_TOUR_STEPS[prevStep - 1].targetRoute
                            navController.navigate(prevTargetRoute) {
                                launchSingleTop = true
                            }
                            viewModel.previousTourStep()
                        }
                    },
                    onSkip = {
                        viewModel.dismissAppTour(markCompleted = true)
                        navController.navigate(NavRoutes.HOME) {
                            popUpTo(NavRoutes.HOME) { inclusive = false }
                        }
                    }
                )
            }
        }
    }
}
