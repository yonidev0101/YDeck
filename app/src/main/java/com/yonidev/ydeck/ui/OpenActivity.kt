package com.yonidev.ydeck.ui

import android.app.Activity
import android.os.Bundle
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.data.Router

/**
 * Invisible hop for widget taps. Starting the browser from a foreground activity avoids
 * background-launch limits, and lets [Router] fall back when a Chrome channel isn't installed.
 */
class OpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val project = Repo.deck(this).byId(intent.getStringExtra(ProjectActivity.EXTRA_PROJECT))
        val action = project?.actions?.getOrNull(intent.getIntExtra(EXTRA_ACTION, -1))
        if (project != null && action != null) Router.open(this, project, action)
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
    }
}
