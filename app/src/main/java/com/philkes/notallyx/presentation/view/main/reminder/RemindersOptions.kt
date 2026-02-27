package com.philkes.notallyx.presentation.view.main.reminder

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel

class RemindersOptions(
    val model: BaseNoteModel,
    val fragment: Fragment,
    val filter: (FilterOptions) -> Unit,
) {
    fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupDetailedReminderObserver()
    }

    fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.reminders_fragment_options, menu)
        menu.findItem(R.id.show_more_details)?.isChecked = model.detailedReminder.value ?: false
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.show_more_details -> {
                val isChecked = !item.isChecked
                item.isChecked = isChecked
                model.detailedReminder.save(isChecked)
                true
            }
            R.id.reminder_filter -> {
                showFilterDialog()
                true
            }
            else -> false
        }
    }

    fun setupDetailedReminderObserver() {
        model.detailedReminder.observe(fragment.viewLifecycleOwner) { isDetailed ->
            val navController = fragment.findNavController()
            val currentDest = navController.currentDestination?.id
            if (isDetailed && currentDest == R.id.Reminders) {
                navController.navigate(R.id.action_reminders_to_detailed)
            } else if (!isDetailed && currentDest == R.id.DetailedReminders) {
                navController.navigate(R.id.action_detailed_to_reminders)
            }
        }
    }

    fun showFilterDialog() {
        val context = fragment.context ?: return
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_reminder_filter)

        dialog.window?.setLayout(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        val radioGroup = dialog.findViewById<RadioGroup>(R.id.filter_radio_group)
        val btnApply = dialog.findViewById<Button>(R.id.apply_filter)

        btnApply.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId

            when (selectedId) {
                R.id.filter_all -> {
                    filter(FilterOptions.ALL)
                }
                R.id.filter_upcoming -> {
                    filter(FilterOptions.UPCOMING)
                }
                R.id.filter_elapsed -> {
                    filter(FilterOptions.ELAPSED)
                }
            }

            Log.d("ReminderOptions", "Filter applied!")
            dialog.dismiss()
        }

        dialog.show()
    }
}

enum class FilterOptions {
    ALL,
    UPCOMING,
    ELAPSED,
}
