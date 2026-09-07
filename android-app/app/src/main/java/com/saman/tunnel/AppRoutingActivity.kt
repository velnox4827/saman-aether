package com.saman.tunnel

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max

class AppRoutingActivity : Activity() {

    private data class AppEntry(
        val packageName: String,
        val label: String
    )

    private val selected = linkedSetOf<String>()
    private var entries: List<AppEntry> = emptyList()

    private lateinit var listContainer: LinearLayout
    private lateinit var modeGroup: RadioGroup
    private lateinit var searchBox: EditText

    // Generated runtime IDs are valid Android view resource IDs.
    private val modeAllId: Int = View.generateViewId()
    private val modeOnlyId: Int = View.generateViewId()
    private val modeBypassId: Int = View.generateViewId()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            SamanVpnService.PREFS,
            MODE_PRIVATE
        )

        selected += prefs.getStringSet(
            SamanVpnService.KEY_SELECTED_APPS,
            emptySet()
        ) ?: emptySet()

        entries = loadLauncherApps()
        buildUi()

        val mode =
            prefs.getString(
                SamanVpnService.KEY_ROUTING_MODE,
                SamanVpnService.ROUTING_ALL
            ) ?: SamanVpnService.ROUTING_ALL

        when (mode) {
            SamanVpnService.ROUTING_ONLY ->
                modeGroup.check(modeOnlyId)

            SamanVpnService.ROUTING_BYPASS ->
                modeGroup.check(modeBypassId)

            else ->
                modeGroup.check(modeAllId)
        }

        renderApps("")
    }

    private fun buildUi() {
        val baseLeft = dp(14)
        val baseTop = dp(12)
        val baseRight = dp(14)
        val baseBottom = dp(12)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(baseLeft, baseTop, baseRight, baseBottom)

            setOnApplyWindowInsetsListener { view, insets ->
                val topInset =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.getInsets(WindowInsets.Type.statusBars()).top
                    } else {
                        @Suppress("DEPRECATION")
                        insets.systemWindowInsetTop
                    }

                val bottomInset =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    } else {
                        @Suppress("DEPRECATION")
                        insets.systemWindowInsetBottom
                    }

                view.setPadding(
                    baseLeft,
                    topInset + baseTop,
                    baseRight,
                    max(baseBottom, bottomInset + dp(6))
                )

                insets
            }
        }

        root.addView(TextView(this).apply {
            text = "VPN app routing"
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text =
                "Choose which apps use Saman Tunnel. " +
                "Checked apps appear first. " +
                "Saman Tunnel itself is automatically protected from VPN loops."
            textSize = 12.5f
            setPadding(0, dp(5), 0, dp(10))
        })

        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        modeGroup.addView(RadioButton(this).apply {
            id = modeAllId
            text = "All apps — everything uses VPN"
        })

        modeGroup.addView(RadioButton(this).apply {
            id = modeOnlyId
            text = "Only selected apps — only checked apps use VPN"
        })

        modeGroup.addView(RadioButton(this).apply {
            id = modeBypassId
            text = "Bypass selected apps — checked apps stay outside VPN"
        })

        root.addView(modeGroup)

        val bulkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(6))
        }

        bulkRow.addView(
            Button(this).apply {
                text = "Select all"
                setOnClickListener {
                    selected.clear()
                    selected += entries.map { entry -> entry.packageName }
                    renderApps(searchBox.text.toString())
                }
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        bulkRow.addView(
            Button(this).apply {
                text = "Clear"
                setOnClickListener {
                    selected.clear()
                    renderApps(searchBox.text.toString())
                }
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(bulkRow)

        searchBox = EditText(this).apply {
            hint = "Search apps or package name"
            isSingleLine = true

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderApps(s?.toString().orEmpty())
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        root.addView(searchBox)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            ScrollView(this).apply {
                addView(listContainer)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(Button(this).apply {
            text = "Save app routing"
            setOnClickListener { saveAndClose() }
        })

        setContentView(root)
        root.requestApplyInsets()
    }

    private fun loadLauncherApps(): List<AppEntry> {
        val launcher = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager
            .queryIntentActivities(launcher, 0)
            .mapNotNull { info ->
                val pkg =
                    info.activityInfo?.packageName
                        ?: return@mapNotNull null

                if (pkg == packageName) {
                    return@mapNotNull null
                }

                val label =
                    runCatching {
                        info.loadLabel(packageManager).toString()
                    }.getOrDefault(pkg)

                AppEntry(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun renderApps(query: String) {
        if (!::listContainer.isInitialized) return

        val q = query.trim().lowercase()
        listContainer.removeAllViews()

        val visible = entries.filter {
            q.isBlank() ||
                it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
        }.sortedWith(
            compareByDescending<AppEntry> { it.packageName in selected }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )

        visible.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }

            row.addView(
                ImageView(this).apply {
                    setImageDrawable(
                        runCatching {
                            packageManager.getApplicationIcon(
                                entry.packageName
                            )
                        }.getOrNull()
                    )

                    adjustViewBounds = true
                },
                LinearLayout.LayoutParams(
                    dp(42),
                    dp(42)
                ).apply {
                    marginEnd = dp(10)
                }
            )

            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            labels.addView(TextView(this).apply {
                text = entry.label
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })

            labels.addView(TextView(this).apply {
                text = entry.packageName
                textSize = 10.5f
            })

            row.addView(
                labels,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val check = CheckBox(this).apply {
                isChecked =
                    selected.contains(entry.packageName)

                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selected += entry.packageName
                    } else {
                        selected -= entry.packageName
                    }
                    // Reorder after this click finishes, keeping the search text.
                    listContainer.post { renderApps(searchBox.text.toString()) }
                }
            }

            row.addView(check)

            row.setOnClickListener {
                check.isChecked = !check.isChecked
            }

            listContainer.addView(row)
        }

        if (visible.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No apps found"
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    dp(24),
                    0,
                    dp(24)
                )
            })
        }
    }

    private fun saveAndClose() {
        val mode =
            when (modeGroup.checkedRadioButtonId) {
                modeOnlyId ->
                    SamanVpnService.ROUTING_ONLY

                modeBypassId ->
                    SamanVpnService.ROUTING_BYPASS

                else ->
                    SamanVpnService.ROUTING_ALL
            }

        if (
            mode == SamanVpnService.ROUTING_ONLY &&
            selected.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Select at least one app for Only selected mode",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val saved =
            getSharedPreferences(
                SamanVpnService.PREFS,
                MODE_PRIVATE
            ).edit()
                .putString(
                    SamanVpnService.KEY_ROUTING_MODE,
                    mode
                )
                .putStringSet(
                    SamanVpnService.KEY_SELECTED_APPS,
                    selected.toSet()
                )
                .commit()

        if (!saved) {
            Toast.makeText(
                this,
                "Could not save app routing",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        LogStore.append(
            this,
            "APP_ROUTING",
            "saved mode=$mode selectedCount=${selected.size}"
        )

        Toast.makeText(
            this,
            "App routing saved — applying now",
            Toast.LENGTH_SHORT
        ).show()

        setResult(RESULT_OK)
        finish()
    }
}
