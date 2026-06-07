// FieldNotes — HiltTestRunner.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (Hilt instrumentation runner)
package com.fieldnotes.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
