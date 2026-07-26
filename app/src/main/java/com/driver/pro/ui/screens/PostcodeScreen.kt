@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.driver.pro.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.driver.pro.network.ApiService
import com.driver.pro.network.SessionManager
import com.driver.pro.utils.isUkMapRegionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/* ----------------------------
   WEBVIEW HOLDER
-----------------------------*/

@SuppressLint("StaticFieldLeak")
object WebViewHolder {

    var webView: WebView? = null

    /** Set from [PostcodeScreen]; must stay on main thread. */
    @Volatile
    var onMapRegionClicked: ((String) -> Unit)? = null

    @Volatile
    var interactionEnabled: Boolean = false

    fun detachFromParent(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
    }

    fun applyInteractionState(wv: WebView, enabled: Boolean) {
        interactionEnabled = enabled
        wv.evaluateJavascript(
            """
            (function(enabled) {
                window.__postcodeInteractionEnabled = enabled;
                if (typeof setPostcodeInteraction === 'function') {
                    setPostcodeInteraction(enabled);
                }
            })($enabled);
            """.trimIndent(),
            null,
        )
    }

    /** Same click logic as idrivesmart postcode_map.html — works with SVG path ids (NN, AB, …). */
    fun injectAndroidMapBridge(wv: WebView) {
        wv.evaluateJavascript(
            """
            (function() {
                var mapEl = document.getElementById('map');
                if (!mapEl) return;
                document.querySelectorAll('canvas').forEach(function(c) {
                    c.style.pointerEvents = 'none';
                });
                if (mapEl.__driverProClickBound) return;
                mapEl.__driverProClickBound = true;
                mapEl.addEventListener('click', function(e) {
                    var group = e.target && e.target.closest ? e.target.closest('g') : null;
                    if (!group) {
                        if (e.target && e.target.tagName === 'path') {
                            group = e.target.parentNode;
                        } else {
                            return;
                        }
                    }
                    var path = group.querySelector ? group.querySelector('path') : null;
                    if (!path && e.target.tagName === 'path') path = e.target;
                    if (!path || !path.id) return;
                    var id = String(path.id).toUpperCase();
                    if (!/^[A-Z]{1,2}$/.test(id)) return;
                    if (window.AndroidApp && AndroidApp.sendEvent) {
                        AndroidApp.sendEvent(id);
                    }
                }, true);
            })();
            """.trimIndent(),
            null,
        )
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun get(context: Context): WebView {
        if (webView == null) {
            webView = WebView(context.applicationContext).apply {
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun sendEvent(postcode: String) {
                            Handler(Looper.getMainLooper()).post {
                                onMapRegionClicked?.invoke(postcode)
                            }
                        }
                    },
                    "AndroidApp",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let {
                            injectAndroidMapBridge(it)
                            applyInteractionState(it, interactionEnabled)
                        }
                    }
                }
                isFocusable = true
                isFocusableInTouchMode = true
                settings.javaScriptEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.domStorageEnabled = true

                loadUrl("https://idrivesmart.co.uk/postcode-map/")
            }
        }
        return webView!!
    }
}

/* ----------------------------
   POSTCODE SCREEN
-----------------------------*/

@Composable
fun PostcodeScreen(
    navController: NavController,
    sessionManager: SessionManager,
    apiService: ApiService,
    userId: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by rememberSaveable { mutableStateOf(false) }
    val enabledState = rememberUpdatedState(enabled)

    val webView = remember { WebViewHolder.get(context) }

    /* ----------------------------
       Load backend enable setting
    -----------------------------*/

    LaunchedEffect(userId) {
        try {
            val response = withContext(Dispatchers.IO) {
                apiService.getPostcodeSetting(userId)
            }
            enabled = response.enable
            WebViewHolder.applyInteractionState(webView, response.enable)
        } catch (e: HttpException) {
            Log.e("API", "Failed loading postcode setting ${e.message()}")
        } catch (e: Exception) {
            Log.e("API", "Failed loading postcode setting ${e.message}")
        }
    }

    /* ----------------------------
       WebView JS interface
    -----------------------------*/

    DisposableEffect(navController) {
        WebViewHolder.onMapRegionClicked = mapClick@{ postcode ->
            val region = postcode.trim().uppercase()
            if (!isUkMapRegionId(region)) {
                Log.w("WEBVIEW", "Ignored map click (not a UK area id): $postcode")
                return@mapClick
            }
            Log.d("WEBVIEW", "Map region clicked: $region")
            val route = "postcodeDetail/${Uri.encode(region)}"
            try {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Log.e("WEBVIEW", "Navigation failed: ${e.message}", e)
            }
        }
        WebViewHolder.injectAndroidMapBridge(webView)
        WebViewHolder.applyInteractionState(webView, enabledState.value)
        onDispose {
            WebViewHolder.onMapRegionClicked = null
        }
    }

    LaunchedEffect(enabled) {
        WebViewHolder.injectAndroidMapBridge(webView)
        WebViewHolder.applyInteractionState(webView, enabled)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                WebViewHolder.injectAndroidMapBridge(webView)
                webView.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /* ----------------------------
       UI
    -----------------------------*/

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WebViewHolder.detachFromParent(webView)
                webView
            },
            update = { wv ->
                wv.requestFocus()
                WebViewHolder.injectAndroidMapBridge(wv)
                WebViewHolder.applyInteractionState(wv, enabledState.value)
            },
        )

        // Switch = use postcodes in auto accept/reject (not "allow opening the list").
        Switch(
            checked = enabled,
            onCheckedChange = { value ->
                scope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            apiService.updatePostcodeSetting(
                                userId,
                                mapOf("enable" to value),
                            )
                        }
                        enabled = response.enable
                    } catch (e: Exception) {
                        Log.e("API", "Failed updating postcode enable: ${e.message}", e)
                        try {
                            val r = withContext(Dispatchers.IO) {
                                apiService.getPostcodeSetting(userId)
                            }
                            enabled = r.enable
                        } catch (e2: Exception) {
                            Log.e("API", "Failed reloading postcode setting: ${e2.message}")
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .scale(0.8f)
                .padding(16.dp),
        )
    }
}
