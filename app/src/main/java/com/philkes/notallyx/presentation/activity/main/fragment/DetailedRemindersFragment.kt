package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.hasAnyUpcomingNotifications
import com.philkes.notallyx.presentation.view.main.reminder.FilterOptions
import com.philkes.notallyx.presentation.view.main.reminder.RemindersOptions

class DetailedRemindersFragment : NotallyFragment() {
    private lateinit var optionsDelegate: RemindersOptions
    private val currentReminderNotes = MutableLiveData<List<Item>>()
    private val allReminderNotes: LiveData<List<Item>> by lazy { model.reminderNotes!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentReminderNotes.value = allReminderNotes.value

        optionsDelegate = RemindersOptions(model, this, ::applyFilter)
        optionsDelegate.onViewCreated(view, savedInstanceState)
    }

    override fun getBackground(): Int = R.drawable.notifications

    override fun getObservable(): LiveData<List<Item>> = currentReminderNotes

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        optionsDelegate.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (optionsDelegate.onOptionsItemSelected(item)) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    fun applyFilter(filterOptions: FilterOptions) {
        val items: List<Item> = allReminderNotes.value ?: return
        val filteredList: List<Item> =
            when (filterOptions) {
                FilterOptions.ALL -> {
                    items
                }
                FilterOptions.UPCOMING -> {
                    items.filter { it is BaseNote && it.reminders.hasAnyUpcomingNotifications() }
                }
                FilterOptions.ELAPSED -> {
                    items.filter { it is BaseNote && !it.reminders.hasAnyUpcomingNotifications() }
                }
            }
        currentReminderNotes.value = filteredList
    }
}
