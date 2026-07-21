package com.tenanatc.cocktailmaker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tenanatc.cocktailmaker.ui.BrowseScreen
import com.tenanatc.cocktailmaker.ui.DetailScreen
import com.tenanatc.cocktailmaker.ui.HomeScreen
import com.tenanatc.cocktailmaker.ui.IngredientsScreen
import com.tenanatc.cocktailmaker.ui.ResultsScreen
import com.tenanatc.cocktailmaker.ui.SettingsDialog
import com.tenanatc.cocktailmaker.ui.theme.CocktailMakerTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CocktailMakerTheme {
                CocktailMakerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CocktailMakerApp(vm: AppViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Absolute path of the file the camera is writing to, for the current capture.
    // Uses rememberSaveable because many OEMs (notably Samsung/One UI) kill the app
    // while the separate camera app is in the foreground; a plain remember would be
    // wiped by that process death and we'd lose the photo on return.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path == null) return@rememberLauncherForActivityResult
        val file = File(path)
        // Don't trust the success flag alone: some camera apps report success=false
        // even after writing the photo. If real bytes landed on disk, use them.
        if (saved || file.length() > 0L) {
            vm.onImageChosen(uriFor(file))
        } else {
            file.delete()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.onImageChosen(uri)
    }

    fun launchCamera() {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", imagesDir)
        pendingCameraPath = file.absolutePath
        takePicture.launch(uriFor(file))
    }

    BackHandler(enabled = vm.screenStack.size > 1) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = vm.currentScreen) {
                            Screen.Home -> stringResource(R.string.app_name)
                            Screen.Ingredients -> "Your Ingredients"
                            Screen.Results -> "Cocktail Ideas"
                            is Screen.Detail -> s.recipe.name
                            Screen.Browse -> "All Recipes"
                        }
                    )
                },
                navigationIcon = {
                    if (vm.screenStack.size > 1) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (vm.currentScreen == Screen.Home) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val screen = vm.currentScreen) {
            Screen.Home -> HomeScreen(
                modifier = modifier,
                hasApiKey = vm.hasRecognitionKey,
                onTakePhoto = {
                    vm.clearSession()
                    launchCamera()
                },
                onPickPhoto = {
                    vm.clearSession()
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onManualSelect = {
                    vm.clearSession()
                    vm.startManualSelection()
                },
                onBrowse = { vm.navigate(Screen.Browse) },
                onOpenSettings = { showSettings = true },
            )

            Screen.Ingredients -> IngredientsScreen(
                modifier = modifier,
                vm = vm,
                onAddPhoto = { launchCamera() },
            )

            Screen.Results -> ResultsScreen(
                modifier = modifier,
                results = vm.results,
                riffs = vm.riffs,
                unlocks = vm.unlocks,
                onOpen = { vm.navigate(Screen.Detail(it.recipe, it)) },
                onOpenRiff = { vm.navigate(Screen.Detail(it)) },
                onAddUnlock = { vm.addUnlockedIngredient(it) },
            )

            is Screen.Detail -> DetailScreen(
                modifier = modifier,
                recipe = screen.recipe,
                match = screen.match,
                data = vm.data,
            )

            Screen.Browse -> BrowseScreen(
                modifier = modifier,
                data = vm.data,
                onOpen = { vm.navigate(Screen.Detail(it)) },
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentGeminiKey = vm.geminiKey,
            currentClarifaiKey = vm.apiKey,
            onSaveGemini = { vm.saveGeminiKey(it) },
            onSaveClarifai = { vm.saveApiKey(it) },
            onDismiss = { showSettings = false },
        )
    }
}
