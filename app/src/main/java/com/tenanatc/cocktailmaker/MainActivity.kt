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

    // A fresh content-Uri in the app's cache dir for each camera capture.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) vm.onImageChosen(uri)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.onImageChosen(uri)
    }

    fun launchCamera() {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", imagesDir)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        takePicture.launch(uri)
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
                hasApiKey = vm.apiKey.isNotBlank(),
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
                onOpen = { vm.navigate(Screen.Detail(it.recipe, it)) },
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
            currentKey = vm.apiKey,
            onSave = { vm.saveApiKey(it) },
            onDismiss = { showSettings = false },
        )
    }
}
