package com.example.callyourmother

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.Long
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.ArrayList

var mContacts = ArrayList<Contacts>()
var mNotification = ArrayList<Contacts>()
lateinit var mPrefs : SharedPreferences

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //initializes shared preferences if first ever start of app
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getString("key", null) == null) {
            val contactArray: ArrayList<Contacts> = ArrayList()
            saveArray(contactArray)
        }
        // https://stackoverflow.com/questions/34342816/android-6-0-multiple-permissions
        //Requests permissions needed
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
                ), 1
            )
        }
        // https://stackoverflow.com/questions/34342816/android-6-0-multiple-permissions
        //Checks for permissions
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissions needed. Restart app and give permissions", Toast.LENGTH_LONG).show()
        }

        val contactsButton = findViewById<Button>(R.id.contacts_button)

        contactsButton.setOnClickListener {
            addAllContacts()
            // https://stackoverflow.com/questions/38892519/store-custom-arraylist-in-sharedpreferences-and-get-it-from-there
            //Pulls shared preferences and checks for any contacts before allowing to proceed to contacts activity
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val gson = Gson()
            val json: String = prefs.getString("key", null) as String
            val type: Type = object : TypeToken<java.util.ArrayList<Contacts>?>() {}.type
            val contactList: ArrayList<Contacts> = gson.fromJson(json, type)
            if (contactList.size > 0) {
                val intent = Intent(this, ContactsActivity::class.java)
                startActivityForResult(intent, 0)
            }
            else {
                Toast.makeText(this, "No contacts", Toast.LENGTH_LONG).show()
            }
        }

        // https://stackoverflow.com/questions/38892519/store-custom-arraylist-in-sharedpreferences-and-get-it-from-there
        // Assigning the mContacts for local use
        val prefsForContact: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val gson = Gson()
        val jsonForContact: String = prefsForContact.getString("key", null) as String
        val type: Type = object : TypeToken<java.util.ArrayList<Contacts>?>() {}.type
        val useList: ArrayList<Contacts> = gson.fromJson(jsonForContact, type)
        mContacts = useList
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        // creating notification array

        mNotification = mContacts.filter { contact: Contacts ->
            when (contact.notification) {
                "Group 1" -> (diffDates(contact.lastCallDate!!) > mPrefs.getInt("Notif1", 1))
                "Group 2" -> (diffDates(contact.lastCallDate!!) > mPrefs.getInt("Notif2", 5))
                "Group 3" -> (diffDates(contact.lastCallDate!!) > mPrefs.getInt("Notif3", 10))
                else -> true
            }
        } as ArrayList<Contacts>


        val notificationButton = findViewById<Button>(R.id.notifications_button)
        notificationButton.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivityForResult(intent, 0)
        }

        // Starts the service for running in background
        val intent : Intent = Intent()
        intent.setClass(applicationContext, RunInBackground::class.java)

        if (!isMyServiceRunning(RunInBackground::class.java))
            startService(intent)
    }

    private fun addAllContacts(){
        // https://stackoverflow.com/questions/12562151/android-get-all-contacts/41827064
        // CREATE CURSOR TO MOVE THROUGH CONTACTS
        val cursor: Cursor = applicationContext.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,null,
            null, null) as Cursor

        // CHECK IF THERE ARE NO CONTACTS ON PHONE
        if (cursor.count == 0) {
            Toast.makeText(this, "No contacts", Toast.LENGTH_LONG).show()
            return
        }
        // https://stackoverflow.com/questions/38892519/store-custom-arraylist-in-sharedpreferences-and-get-it-from-there
        // PULL THE CURRENT CONTACT LIST FROM SHARED PREFERENCES
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val gson = Gson()
        val json: String = prefs.getString("key", null) as String
        val type: Type = object : TypeToken<java.util.ArrayList<Contacts>?>() {}.type
        val useList: ArrayList<Contacts> = gson.fromJson(json, type)
        val contactList: ArrayList<Contacts> = ArrayList()

        // https://stackoverflow.com/questions/12562151/android-get-all-contacts/41827064
        // MOVE THROUGH ALL CONTACTS
        while (cursor.moveToNext()) {
            // https://developer.android.com/reference/android/provider/ContactsContract
            // GET IMAGE FROM CONTACT
            val bitmap = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI))

            // https://developer.android.com/reference/android/provider/ContactsContract
            // https://gist.github.com/srayhunter/47ab2816b01f0b00b79150150feb2eb2
            // GET PHONE NUMBER FROM CONTACT
            val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
            var phone = ""
            if (cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                val phoneCursor: Cursor = applicationContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf<String>(id),
                    null
                )!!
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    phone = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    phoneCursor.close()
                }
            }

            // https://developer.android.com/reference/android/provider/ContactsContract
            // GET NAME FROM CONTACT
            val name =
                cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))

            // GET NOTIFICATION GROUP
            var notificationGroup = "Group 2"
            for (check in useList) {
                if (check.phone == phone) {
                    notificationGroup = check.notification as String
                }
            }

            // https://developer.android.com/reference/android/provider/ContactsContract
            // GET DATE FROM CONTACT (HELPER FUNCTION)
            val date: Date = getDate(phone)

            // CREATE CONTACT WITH COLLECTED INFORMATION
            val contact = Contacts(bitmap, phone, name, notificationGroup, date)

            contactList.add(contact)
        }
        // SAVE THE ARRAY TO SHARED PREFERENCES (HELPER FUNCTION)
        saveArray(contactList)
    }

    // TAKES IN A NUMBER AND RETURNS THE MOST RECENT DATE THAT NUMBER WAS CALLED BY THE USER
    private fun getDate(phone: String): Date {
        // https://stackoverflow.com/questions/12562151/android-get-all-contacts/41827064
        // CREATE A CURSOR OF CALL LOGS
        val allCalls: Uri = Uri.parse("content://call_log/calls")
        val cursor: Cursor = applicationContext.contentResolver.query(allCalls, null,
            null, null, null) as Cursor

        // REMOVE PHONE NUMBER FORMATTING FROM GIVEN NUMBER
        var callDate = ""
        val re = Regex("-| |\\(|\\)")
        val newPhone = re.replace(phone, "")

        // CHECKER TO MAKE SURE MOST RECENT LOG ISN'T REPLACED BY OLDER LOG
        var notFound = true
        // https://stackoverflow.com/questions/12562151/android-get-all-contacts/41827064
        // ITERATE THROUGH LOGS UNTIL MATCH IS FOUND
        while (cursor.moveToNext() && notFound) {
            // MATCH IS FOUND IF THE PHONE NUMBER GIVEN IS THE PHONE NUMBER OF THE CURRENT LOG
            if (newPhone == cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))) {
                // GET THE DATE
                val date: Int = cursor.getColumnIndex(CallLog.Calls.DATE)
                callDate = cursor.getString(date)
                notFound = false
            }
        }
        // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.js/-date/
        // IF LOG IS NOT FOUND, RETURN FILLER DATE
        if (notFound) {
            return Date(1, 1, 1900)
        }
        return Date(Long.valueOf(callDate))
    }

    // SAVE THE ARRAY TO SHARED PREFERENCES USING JSON
    // https://stackoverflow.com/questions/38892519/store-custom-arraylist-in-sharedpreferences-and-get-it-from-there
    private fun saveArray(contactList: ArrayList<Contacts>) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        val gson = Gson()
        val jsonText = gson.toJson(contactList)
        editor.putString("key", jsonText)
        editor.commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        //Add menu options to main activity screen
        menu.add(Menu.NONE, 1, Menu.NONE, "Clear Notifications")
        menu.add(Menu.NONE, 2, Menu.NONE, "Edit Notification Groups")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                //Clearing notifications
                mPrefs.edit().putBoolean("cleared", true).commit()
                return true
            }

            2 -> {
                val dialogBuilder = AlertDialog.Builder(this)
                val ndialog = layoutInflater.inflate(
                    R.layout.notificationgroupdialog,
                    null
                )  //Custom Dialog for entering number of days per Notification group

                val notif1 = ndialog.findViewById<EditText>(R.id.notification1)
                val notif2 = ndialog.findViewById<EditText>(R.id.notification2)
                val notif3 = ndialog.findViewById<EditText>(R.id.notification3)

                notif1.hint = mPrefs.getInt("Notif1", 1).toString()
                notif2.hint = mPrefs.getInt("Notif2", 5).toString()
                notif3.hint = mPrefs.getInt("Notif3", 10).toString()

                //Sets default notification intervals per group if no prior settings are found
                if (notif1.text.isEmpty() || notif1.text.toString().toInt() == 0) {
                    notif1.setText(mPrefs.getInt("Notif1", 1).toString())
                }
                if (notif2.text.isEmpty() || notif2.text.toString().toInt() == 0) {
                    notif2.setText(mPrefs.getInt("Notif2", 5).toString())
                }
                if (notif3.text.isEmpty() || notif3.text.toString().toInt() == 0) {
                    notif3.setText(mPrefs.getInt("Notif3", 10).toString())
                }


                dialogBuilder.setView(ndialog)

                //Title bar styling for dialog inspired from https://stackoverflow.com/a/13359573
                val title = TextView(this)
                title.text = "Notification Group Settings"
                title.setBackgroundColor(resources.getColor(R.color.colorPrimary))
                title.setPadding(20, 20, 20, 20)
                title.gravity = Gravity.CENTER
                title.setTextColor(Color.WHITE)
                title.textSize = 20f
                dialogBuilder.setCustomTitle(title)

                val dialog = dialogBuilder.create() //Create and display the dialog to the user
                dialog.show()

                val saveButton = ndialog.findViewById<Button>(R.id.saveButton)
                saveButton.setOnClickListener {
                    // Edit notification groups and backend monitoring

                    val prefs: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val editor = prefs.edit()

                    //Validates notification intervals
                    val v1 = if (notif1.text.isEmpty() || notif1.text.toString().toInt() == 0) {
                        mPrefs.getInt("Notif1", 1).toString()
                    } else notif1.text.toString()
                    val v2 = if (notif2.text.isEmpty() || notif2.text.toString().toInt() == 0) {
                        mPrefs.getInt("Notif2", 5).toString()
                    } else notif2.text.toString()
                    val v3 = if (notif3.text.isEmpty() || notif3.text.toString().toInt() == 0) {
                        mPrefs.getInt("Notif3", 10).toString()
                    } else notif3.text.toString()

                    //Saves notification intervals to shared preferences
                    editor.putInt("Notif1", v1.toInt())
                    editor.putInt("Notif2", v2.toInt())
                    editor.putInt("Notif3", v3.toInt())
                    editor.commit()
                    dialog.dismiss()
                }

                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun diffDates (date : Date) : kotlin.Long {
        val cal : Date = Calendar.getInstance().time
        val diff: kotlin.Long = cal.time - date.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return hours / 24
    }

    override fun onDestroy() {
        stopService(Intent(applicationContext, RunInBackground::class.java))
        val broadcastIntent = Intent().setAction("restartservice").setClass(this, Restarter::class.java)
        sendBroadcast(broadcastIntent)
        mPrefs.edit().putBoolean("cleared", false).commit()
        super.onDestroy()
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
