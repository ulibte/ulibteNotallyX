package com.philkes.notallyx.utils

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel

class RemindersOptionsDelegate(val model: BaseNoteModel, val fragment: Fragment) {
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
}
