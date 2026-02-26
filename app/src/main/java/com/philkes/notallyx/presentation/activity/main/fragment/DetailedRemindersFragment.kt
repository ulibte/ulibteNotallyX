package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.LiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.utils.RemindersOptionsDelegate

class DetailedRemindersFragment : NotallyFragment() {
    private lateinit var optionsDelegate: RemindersOptionsDelegate

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        optionsDelegate = RemindersOptionsDelegate(model, this)
        optionsDelegate.onViewCreated(view, savedInstanceState)
    }

    override fun getBackground(): Int = R.drawable.notifications

    override fun getObservable(): LiveData<List<Item>> = model.reminderNotes!!

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
}
