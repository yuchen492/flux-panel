package com.flux

import androidx.core.content.edit
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import android.webkit.*
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        WebView.setWebContentsDebuggingEnabled(true)
        setupWebView()
        loadUrl()

        setupSystemBars()
        setupRootBackground()
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // 隐藏滚动条但不影响滚动
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_NEVER


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        webView.setBackgroundColor(if (isDarkTheme) Color.BLACK else Color.WHITE)


        // 添加JavaScript接口
        webView.addJavascriptInterface(object {

            @android.webkit.JavascriptInterface
            fun getPanelAddresses(callback: String) {
                val sharedPrefs = getSharedPreferences("panel_config", MODE_PRIVATE)
                val addresses = sharedPrefs.getString("panel_addresses", "[]")
                webView.post {
                    webView.evaluateJavascript("window.$callback($addresses);", null)
                }
            }

            @android.webkit.JavascriptInterface
            fun savePanelAddress(name: String, address: String) {
                val json = JSONObject()
                json.put("name", name)
                json.put("address", address)
                json.put("inx", false)
                val sharedPrefs = getSharedPreferences("panel_config", MODE_PRIVATE)
                val addresses = sharedPrefs.getString("panel_addresses", "[]")
                val jsonArray = JSONArray(addresses)
                jsonArray.put(json)
                sharedPrefs.edit {
                    putString("panel_addresses", jsonArray.toString())
                }
                webView.post {
                    webView.evaluateJavascript("window.setPanelAddresses(${jsonArray.toString()});", null)
                }
            }

            @android.webkit.JavascriptInterface
            fun setCurrentPanelAddress(name: String) {

                val sharedPrefs = getSharedPreferences("panel_config", MODE_PRIVATE)
                val addresses = sharedPrefs.getString("panel_addresses", "[]")
                val jsonArray = JSONArray(addresses)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (name == obj.getString("name")) {
                        obj.put("inx", true)
                    }else{
                        obj.put("inx", false)
                    }
                }
                sharedPrefs.edit {
                    putString("panel_addresses", jsonArray.toString())
                }
                webView.post {
                    webView.evaluateJavascript("window.setPanelAddresses(${jsonArray.toString()});", null)
                }
            }

            @android.webkit.JavascriptInterface
            fun deletePanelAddress(name: String) {
                val jsonArray = JSONArray()
                val sharedPrefs = getSharedPreferences("panel_config", MODE_PRIVATE)
                val addresses = sharedPrefs.getString("panel_addresses", "[]")
                val jsonArraya = JSONArray(addresses)
                for (i in 0 until jsonArraya.length()) {
                    val obj = jsonArraya.getJSONObject(i)
                    if (name != obj.getString("name")) {
                        jsonArray.put(obj)
                    }
                }
                sharedPrefs.edit {
                    putString("panel_addresses", jsonArray.toString())
                }
                webView.post {
                    webView.evaluateJavascript("window.setPanelAddresses(${jsonArray.toString()});", null)
                }
            }
        }, "JsInterface")

    }

    private fun loadUrl() {
        //webView.loadUrl("http://192.168.100.9:3000")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun setupSystemBars() {
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }
        } else {
            window.statusBarColor = Color.WHITE
            window.navigationBarColor = Color.WHITE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupSystemBars()
        setupWebViewBackground()
        setupRootBackground()
    }

    private fun setupWebViewBackground() {
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        webView.setBackgroundColor(if (isDarkTheme) Color.BLACK else Color.WHITE)
    }

    private fun setupRootBackground() {
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setBackgroundColor(if (isDarkTheme) Color.BLACK else Color.WHITE)
    }
}
