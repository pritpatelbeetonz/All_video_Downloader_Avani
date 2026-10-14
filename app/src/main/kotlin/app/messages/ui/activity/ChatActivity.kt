package app.messages.ui.activity

import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.os.Bundle
import android.telephony.SmsManager
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.content.ContentValues
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.SyncManager
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.MessageEntity
import app.messages.databinding.ActivityChatBinding
import app.messages.databinding.PopupChatInfoMenuBinding
import app.messages.utility.SimManager
import app.messages.ui.adapters.MessageAdapter
import app.messages.ui.adapters.ChatItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Message
import app.messages.Controller
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import app.messages.ui.dialogs.SimSelectionDialog
import app.messages.utility.SimInfo
import app.messages.ui.dialogs.GeneralDialogs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import android.provider.ContactsContract

import kotlinx.coroutines.withContext
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.data.db.ConversationEntity
import app.messages.utility.AddressUtils

class ChatActivity : BaseActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var db: LauncherDatabase
    private lateinit var syncManager: SyncManager
    private lateinit var simManager: SimManager
    private var selectedSim: SimInfo? = null  // Store selected SIM
    private var isScheduling: Boolean = false
    private var scheduledTimestamp: Long = 0L

    private var threadId: Long = -1
    private var address: String = ""
    private var recipientName: String? = null
    private var photoUri: String? = null
    private var syncJob: kotlinx.coroutines.Job? = null
    private var observationJob: kotlinx.coroutines.Job? = null

    private var tempImageUri: Uri? = null
    private var tempCameraUri: Uri? = null
    private var isBlocked: Boolean = false
    private var isSaved: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            tempImageUri = uri
            showImagePreview(uri)
            binding.llAttachment.visibility = View.GONE
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            tempImageUri = tempCameraUri
            showImagePreview(tempCameraUri!!)
            binding.llAttachment.visibility = View.GONE
        }
    }

    private val contentObserver = object :
        android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            triggerSync()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        app.messages.utility.ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = LauncherDatabase.getLauncherDatabase(this)
        syncManager = SyncManager(this)
        simManager = SimManager(this)
        applySystemBarInsets(app.messages.R.color.bg_main, !isNightModeActive())

        // ensure layout adjusts for keyboard so input field isn't obscured
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        handleIncomingIntent(intent)

        savedInstanceState?.let { bundle ->
            bundle.getString("temp_image_uri")?.let {
                tempImageUri = Uri.parse(it)
                showImagePreview(tempImageUri!!)
            }
            bundle.getString("temp_camera_uri")?.let {
                tempCameraUri = Uri.parse(it)
            }
        }

        applyWindowInsetListener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun extractAddressFromIntent(inIntent: Intent?): String {
        if (inIntent == null) return ""
        
        // 1. Direct "address" extra
        val directAddress = inIntent.getStringExtra("address")
        if (!directAddress.isNullOrBlank()) return directAddress

        // 2. EXTRA_PHONE_NUMBER
        val phoneExtra = inIntent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        if (!phoneExtra.isNullOrBlank()) return phoneExtra

        val phoneArray = inIntent.getStringArrayExtra(Intent.EXTRA_PHONE_NUMBER)
        if (!phoneArray.isNullOrEmpty()) return phoneArray.joinToString(",")

        // 3. Data URI (e.g. smsto:7984521320, sms:+917984521320, etc.)
        val data = inIntent.data
        if (data != null) {
            val ssp = data.schemeSpecificPart
            if (!ssp.isNullOrBlank()) {
                val cleanSsp = if (ssp.contains("?")) ssp.substringBefore("?") else ssp
                if (cleanSsp.isNotBlank()) return Uri.decode(cleanSsp)
            }
            val host = data.host
            if (!host.isNullOrBlank()) {
                return host
            }
        }

        // 4. "recipients" / "recipient"
        val recipient = inIntent.getStringExtra("recipient")
        if (!recipient.isNullOrBlank()) return recipient

        val recipientsArray = inIntent.getStringArrayExtra("recipients")
        if (!recipientsArray.isNullOrEmpty()) return recipientsArray.joinToString(",")

        return ""
    }

    private fun extractBodyFromIntent(inIntent: Intent?): String {
        if (inIntent == null) return ""
        val extraText = inIntent.getStringExtra(Intent.EXTRA_TEXT)
        if (!extraText.isNullOrBlank()) return extraText
        
        val smsBody = inIntent.getStringExtra("sms_body")
        if (!smsBody.isNullOrBlank()) return smsBody
        
        val data = inIntent.data
        if (data != null) {
            if (data.isHierarchical) {
                try {
                    val bodyParam = data.getQueryParameter("body")
                    if (!bodyParam.isNullOrBlank()) return bodyParam
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // For opaque URIs like smsto:12345?body=Hello
                val ssp = data.schemeSpecificPart
                if (ssp != null && ssp.contains("?body=")) {
                    return ssp.substringAfter("?body=").substringBefore("&")
                }
            }
        }
        return ""
    }

    private fun resolveContactDetails(phoneNumber: String): Pair<String?, String?> {
        if (phoneNumber.isBlank()) return Pair(null, null)
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val thumbIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val photo = if (photoIdx != -1) cursor.getString(photoIdx) else if (thumbIdx != -1) cursor.getString(thumbIdx) else null
                    return Pair(name, photo)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(null, null)
    }

    private fun handleIncomingIntent(inIntent: Intent) {
        val extractedRawAddress = extractAddressFromIntent(inIntent)
        val rawThreadId = inIntent.getLongExtra("thread_id", -1L)
        
        threadId = rawThreadId
        address = AddressUtils.normalizeAddress(extractedRawAddress)
        recipientName = inIntent.getStringExtra("name")
        photoUri = inIntent.getStringExtra("photo_uri")
        isSaved = inIntent.getBooleanExtra("is_saved", false)
        isScheduling = inIntent.getBooleanExtra("is_scheduling", false)

        val prefilledBody = extractBodyFromIntent(inIntent)
        if (prefilledBody.isNotBlank()) {
            binding.etMessage.setText(prefilledBody)
        }

        setupUI()
        setupDataObservation()

        // Load Draft
        val draftKey = if (threadId != -1L) threadId.toString() else address
        if (draftKey.isNotBlank()) {
            val draft = app.messages.utility.DraftManager.getDraft(this, draftKey)
            if (draft != null && binding.etMessage.text.isEmpty()) {
                binding.etMessage.setText(draft)
                binding.etMessage.setSelection(draft.length)
            }
        }

        if (address.isNotBlank()) {
            if (recipientName.isNullOrBlank() || photoUri.isNullOrBlank()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val contactInfo = resolveContactDetails(address)
                    withContext(Dispatchers.Main) {
                        if (recipientName.isNullOrBlank() && contactInfo.first != null) {
                            recipientName = contactInfo.first
                        }
                        if (photoUri.isNullOrBlank() && contactInfo.second != null) {
                            photoUri = contactInfo.second
                        }
                        updateHeaderTitleAndSubtitle()
                        bindHeaderAvatars()
                    }
                }
            }
            observeThreadIdFromAddress()
        }

        if (threadId != -1L) {
            app.messages.Controller.activeThreadId = threadId
            triggerSync()
        } else {
            attemptResolveThread()
        }

        if (isScheduling) {
            showDateTimePicker()
        }
    }


    private fun applyWindowInsetListener() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = ime.bottom > 0

            // Keep status bar top inset with bg_main background matching ChatsFragment
            if (isKeyboardVisible) {
                v.setPadding(sysBars.left, sysBars.top, sysBars.right, 0)
            } else {
                v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            }
            insets
        }

        // Handle bottom inset for keyboard specifically for layoutBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottom) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val params = view.layoutParams as ViewGroup.MarginLayoutParams

            // When keyboard is visible, root padding is 0, so we add keyboard height here.
            // When keyboard is hidden, root padding is sysBars.bottom, so we add 0 here.
            if (ime.bottom > 0) {
                params.bottomMargin = ime.bottom
            } else {
                params.bottomMargin = 0
            }
            view.layoutParams = params
            insets
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tempImageUri?.let {
            outState.putString("temp_image_uri", it.toString())
        }
        tempCameraUri?.let {
            outState.putString("temp_camera_uri", it.toString())
        }
    }

    private fun showImagePreview(uri: Uri) {
        binding.flImagePreview.visibility = View.VISIBLE
        Glide.with(this).load(uri).into(binding.ivPreview)
        updateSendButtonState()
    }

    private fun hideImagePreview() {
        binding.flImagePreview.visibility = View.GONE
        binding.ivPreview.setImageDrawable(null)
        tempImageUri = null
        tempCameraUri = null
        updateSendButtonState()
    }

    private fun showInfoMenu(view: View) {
        lifecycleScope.launch {
            // Check blocked status
            val blockedAddresses = db.blockedContactDao().getAllBlockedAddresses()
            val members = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            isBlocked = members.any { it in blockedAddresses } || blockedAddresses.contains(address.trim())

            // Inflate custom popup layout
            val menuBinding = PopupChatInfoMenuBinding.inflate(
                LayoutInflater.from(this@ChatActivity)
            )

            // Create PopupWindow
            val popupWindow = android.widget.PopupWindow(
                menuBinding.root,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // Set background to enable dismiss on outside touch
            popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            popupWindow.elevation = 8f

            if (address.contains(",")) {
                menuBinding.llGroupInfo.visibility = android.view.View.VISIBLE
                menuBinding.vGroupInfoDivider.visibility = android.view.View.VISIBLE
            }

            // Update Block/Unblock text and icon
            if (isBlocked) {
                menuBinding.tvBlock.text = getString(R.string.unblock)
                // Optionally change icon or keep it same as ic_block is often used for both.
            } else {
                menuBinding.tvBlock.text = getString(R.string.block)
            }

            // Set click listeners
            menuBinding.llAddPeople.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvAddPeople)
                popupWindow.dismiss()
            }

            menuBinding.llGroupInfo.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                val intent = Intent(this@ChatActivity, GroupInfoActivity::class.java)
                intent.putExtra("thread_id", threadId)
                intent.putExtra("address", address)
                intent.putExtra("name", recipientName)
                intent.putExtra("photo_uri", photoUri)
                startActivity(intent)

                popupWindow.dismiss()
            }

            menuBinding.llCopy.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvCopy)
                popupWindow.dismiss()
            }
            menuBinding.llCall.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvCall)
                popupWindow.dismiss()
            }
            menuBinding.llRestoreLastMessage.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvRestoreLastMessage)
                popupWindow.dismiss()
            }
            menuBinding.llBlock.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvBlock)
                popupWindow.dismiss()
            }
            menuBinding.llDelete.setOnClickListener {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
                applyTypeFilter(menuBinding.tvDelete)
                popupWindow.dismiss()
            }

            // Show popup below the icon
            popupWindow.showAsDropDown(view, 0, 8)
        }
    }

    private val addPeopleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult

                val threadId = data.getLongExtra("thread_id", -1)
                val address = data.getStringExtra("address") ?: ""
                val name = data.getStringExtra("name")

                openNewChat(threadId, address, name)
            }
        }

    private val pickContactLauncher = 
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            uri?.let { onContactSelected(it) }
        }

    private fun onContactSelected(contactUri: Uri) {
        val cursor = contentResolver.query(contactUri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            
            if (nameIndex != -1 && idIndex != -1) {
                val name = cursor.getString(nameIndex)
                val id = cursor.getString(idIndex)
                
                // Now get the phone number
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                
                var phoneNumber = ""
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex != -1) {
                        phoneNumber = phoneCursor.getString(numberIndex)
                    }
                    phoneCursor.close()
                }
                
                val contactInfo = if (phoneNumber.isNotEmpty()) {
                    "Contact: $name\nNumber: $phoneNumber"
                } else {
                    "Contact: $name"
                }
                
                val currentText = binding.etMessage.text.toString()
                if (currentText.isEmpty()) {
                    binding.etMessage.setText(contactInfo)
                } else {
                    binding.etMessage.setText("$currentText\n$contactInfo")
                }
                binding.etMessage.setSelection(binding.etMessage.text.length)
                binding.llAttachment.visibility = View.GONE
            }
            cursor.close()
        }
    }

    private fun openNewChat(threadId: Long, address: String, name: String?) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("thread_id", threadId)
            putExtra("address", address)
            putExtra("name", name)
        }

        startActivity(intent)
        finish()
    }

    private fun applyTypeFilter(view: TextView) {

        when (view.id) {
            R.id.tvAddPeople -> {
                val intent = Intent(this, AddPeopleActivity::class.java)
                intent.putExtra("existing_members", address)
                addPeopleLauncher.launch(intent)
//                InterAdLoader.startActivityWithAdLauncher(this, intent,addPeopleLauncher)
                addPeopleLauncher.launch(intent)
            }

            R.id.tvCopy -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Address", address)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    this,
                    getString(R.string.contact_copied_to_clipboard), Toast.LENGTH_SHORT
                ).show()
            }

            R.id.tvCall -> {
                try {
                    val number = address.split(",")
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                        ?: address
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$number")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to make a call", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.tvRestoreLastMessage -> {
                if (threadId != -1L) {
                    triggerSync()
                    Toast.makeText(
                        this,
                        getString(R.string.restoring_last_messages), Toast.LENGTH_SHORT
                    ).show()
                }
            }

            R.id.tvBlock -> {
                lifecycleScope.launch {
                    if (isBlocked) {
                        app.messages.utility.ConversationActionHelper.unblockConversations(
                            this@ChatActivity,
                            listOf(address),
                            showToast = true,
                            onSuccess = {  } // isBlocked will be re-evaluated on next menu show
                        )
                    } else {
                        app.messages.utility.ConversationActionHelper.blockConversations(
                            this@ChatActivity,
                            listOf(threadId).filter { it != -1L },
                            listOf(address),
                            showToast = true,
                            onSuccess = { finish() }
                        )
                    }
                }
            }

            R.id.tvDelete -> {
                GeneralDialogs.showStyledDeleteDialog(
                    this,
                    getString(R.string.are_you_sure_you_want_to_delete_this_conversation)
                ) {
                    lifecycleScope.launch {
                        if (threadId != -1L) {
                            app.messages.utility.ConversationActionHelper.deleteConversations(
                                this@ChatActivity,
                                listOf(threadId),
                                showToast = true,
                                onSuccess = { finish() }
                            )
                        } else {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun observeThreadIdFromAddress() {
        lifecycleScope.launch {
            val normalized = app.messages.utility.AddressUtils.normalizeAddress(address)
            db.conversationDao().getFlowByNormalizedAddress(normalized).collectLatest { conversation ->
                if (conversation != null && conversation.threadId != threadId) {
                    val oldId = threadId
                    val newId = conversation.threadId

                    // Merge if switching from temporary negative ID to a discovered system ID
                    if (oldId < 0 && (newId >= 0 || (newId < 0 && newId != oldId))) {
                        db.messageDao().mergeMessages(oldId, newId)
                        db.conversationDao().deleteByThreadId(oldId)
                    }

                    threadId = newId
                    app.messages.Controller.activeThreadId = threadId
                    setupDataObservation()
                }
            }
        }
    }

    private fun attemptResolveThread() {
        lifecycleScope.launch(Dispatchers.IO) {
            val normalized = app.messages.utility.AddressUtils.normalizeAddress(address)

            // 1. Try the local DB first using the normalized address index (fastest)
            var conversation = db.conversationDao().getByNormalizedAddress(normalized)

            // 2. Fall back to raw address match (handles non-standard addresses like short codes)
            if (conversation == null) {
                conversation = db.conversationDao().getByAddress(address)
            }

            // 3. Fall back to the system Telephony provider to get the real thread ID
            //    This is the critical path: an existing SMS thread WILL be found here even if
            //    the local Room DB hasn't synced it yet.
            if (conversation == null && address.isNotBlank()) {
                try {
                    val systemThreadId = android.provider.Telephony.Threads.getOrCreateThreadId(this@ChatActivity, address)
                    if (systemThreadId > 0L) {
                        threadId = systemThreadId
                        app.messages.Controller.activeThreadId = threadId
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            updateHeaderTitleAndSubtitle()
                            bindHeaderAvatars()
                            setupDataObservation()
                        }
                        // Sync will pull all messages for this system thread
                        syncManager.syncMessagesForThread(threadId)
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 4. We found a conversation in Room — update local state and sync
            if (conversation != null) {
                val resolvedId = conversation.threadId
                val resolvedName = conversation.recipientName
                val resolvedPhoto = conversation.photoUri
                val resolvedSaved = conversation.isSaved

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    threadId = resolvedId
                    recipientName = resolvedName
                    photoUri = resolvedPhoto
                    isSaved = resolvedSaved
                    app.messages.Controller.activeThreadId = threadId
                    updateHeaderTitleAndSubtitle()
                    bindHeaderAvatars()
                    // Restart observation with the real threadId so the address+threadId query works
                    setupDataObservation()
                }
                triggerSync() // Sync messages for this known thread
            }
        }
    }

    private fun updateHeaderTitleAndSubtitle() {
        val displayName = recipientName?.takeIf { it.isNotBlank() }
        if (displayName != null && displayName != address) {
            binding.tvName.text = displayName
            binding.tvSubtitle.visibility = android.view.View.VISIBLE
            binding.tvSubtitle.text = address
            binding.tvSubtitle.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            binding.tvName.text = address
            binding.tvSubtitle.visibility = android.view.View.GONE
        }
    }

    private fun setupUI() {
        updateHeaderTitleAndSubtitle()

        bindHeaderAvatars()

        binding.btnBack.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.CHAT)
            finish()
        }

        binding.btnCall.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
            try {
                val number = address.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: address
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to make a call", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSimSelection.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
            val availableSims = simManager.getAvailableSims()
            if (availableSims.size > 1) {
                showSimSelectionDialog(availableSims, null, null, false)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.only_one_sim_card_available), Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.txtContact.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_ATTACHMENT, EventScreenName.CHAT)
            try {
                pickContactLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open contacts", Toast.LENGTH_SHORT).show()
            }
            binding.llAttachment.visibility = View.GONE
            binding.btnPlus.setImageResource(R.drawable.ic_attach)
        }

        binding.txtSchedule.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_SCHEDULE, EventScreenName.CHAT)
            showDateTimePicker()
            binding.llAttachment.visibility = View.GONE
            binding.btnPlus.setImageResource(R.drawable.ic_attach)
        }

        binding.txtPhoto.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_ATTACHMENT, EventScreenName.CHAT)
            pickImageLauncher.launch("image/*")
        }

        binding.txtCamera.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_ATTACHMENT, EventScreenName.CHAT)
            launchCamera()
        }

        binding.ivRemovePreview.setOnClickListener {
            hideImagePreview()
        }

        // Initialize selectedSim from the last message in the thread if available, 
        // otherwise fallback to system default.
        lifecycleScope.launch(Dispatchers.IO) {
            val lastMsg = db.messageDao().getLastMessageForThreadSync(threadId)
            val subId = lastMsg?.subscriptionId?.toInt() ?: SmsManager.getDefaultSmsSubscriptionId()
            
            withContext(Dispatchers.Main) {
                if (subId != -1) {
                    selectedSim = simManager.getSimForSubscriptionId(subId.toLong())
                }
                
                if (selectedSim == null) {
                    selectedSim = simManager.getDefaultSim() ?: simManager.getAvailableSims().firstOrNull()
                        .takeIf { simManager.getAvailableSims().size == 1 }
                }
                updateSimButton()
            }
        }

        adapter = MessageAdapter()
        adapter.setSimManager(simManager)
        adapter.setDeliveryReportsEnabled(PreferenceHelper.getInstance(this).getDeliveryReportsEnabled())
        adapter.onStopClick = { msg ->
            cancelDelayedMessage(msg)
        }
        adapter.onResendClick = { msg ->
            // Delete the failed message locally and from the system provider, then resend it
            lifecycleScope.launch {
                db.messageDao().deleteByIds(listOf(msg.id))
                try {
                    val deleteUri = android.content.ContentUris.withAppendedId(
                        android.provider.Telephony.Sms.CONTENT_URI, msg.id
                    )
                    contentResolver.delete(deleteUri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                sendMessage(msg.body, msg.imageUri)
            }
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
        (binding.rvMessages.itemAnimator as? androidx.recyclerview.widget.DefaultItemAnimator)?.supportsChangeAnimations =
            false

        updateSendButtonState()

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState()
            }
        })

        binding.btnSend.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_SEND, EventScreenName.CHAT)
            val text = binding.etMessage.text.toString()
            if (text.isNotBlank() || tempImageUri != null) {
                sendMessage(text, tempImageUri?.toString())
            }
        }

        binding.btnInfo.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
            showInfoMenu(binding.btnInfo)
        }

        binding.btnCancelSchedule.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.SCHEDULE_CLICK_CANCEL, EventScreenName.CHAT)
            isScheduling = false
            scheduledTimestamp = 0L
            binding.layoutScheduledIndicator.visibility = View.GONE
            binding.dividerScheduleMessage.visibility = View.GONE
        }

        binding.btnPlus.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_ATTACHMENT, EventScreenName.CHAT)
            if (binding.llAttachment.visibility == View.VISIBLE) {
                binding.llAttachment.visibility = View.GONE
                binding.btnPlus.setImageResource(R.drawable.ic_attach)
            } else {
                binding.llAttachment.visibility = View.VISIBLE
                binding.emojiPickerView.visibility = View.GONE
                binding.btnPlus.setImageResource(R.drawable.ic_clearsearch)
            }
        }

        setupEmojiPicker()

        binding.flAvatarContainer.setOnClickListener {
            val intent = Intent(this, GroupInfoActivity::class.java)
            intent.putExtra("thread_id", threadId)
            intent.putExtra("address", address)
            intent.putExtra("name", recipientName)
            intent.putExtra("photo_uri", photoUri)
            startActivity(intent)
        }

        binding.tvName.setOnClickListener {
            val intent = Intent(this, GroupInfoActivity::class.java)
            intent.putExtra("thread_id", threadId)
            intent.putExtra("address", address)
            intent.putExtra("name", recipientName)
            intent.putExtra("photo_uri", photoUri)
            startActivity(intent)
        }

        setupSelectionMode()
        setupReadOnlyState()

        lifecycleScope.launch {
            app.messages.config.AdsConfigManager.configFlow.collectLatest {
                setupReadOnlyState()
            }
        }
    }

    private fun setupReadOnlyState() {
        val canReply = app.messages.utility.AddressUtils.canReply(address)
        if (!canReply) {
            binding.llComposeBar.visibility = View.GONE
            binding.llAttachment.visibility = View.GONE
            binding.llReadOnlyBar.visibility = View.VISIBLE

            // Load Banner Ad with Shimmer
            val config = app.messages.config.AdsConfigManager.config
            val frameLayout = binding.bannerViewAdsFrameLayout.bannerAdFrameLayout
            val shimmerFrameLayout = binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout

            if (config.isBannerOn && !app.messages.preferences.PreferenceHelper.getInstance(this).isPremium()) {
                shimmerFrameLayout.visibility = View.VISIBLE
                frameLayout.visibility = View.GONE

                val adView = app.messages.config.AdsConfigManager.getBannerAd(this, config.bannerAdIdContactHome)
                if (adView != null) {
                    if (adView.parent != null) {
                        (adView.parent as ViewGroup).removeView(adView)
                    }
                    frameLayout.addView(adView)

                    if (adView.responseInfo != null) {
                        frameLayout.visibility = View.VISIBLE
                        shimmerFrameLayout.visibility = View.GONE
                        adView.visibility = View.VISIBLE
                    }

                    adView.adListener = object : com.google.android.gms.ads.AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            if (!isDestroyed && !isFinishing) {
                                frameLayout.visibility = View.VISIBLE
                                shimmerFrameLayout.visibility = View.GONE
                                adView.visibility = View.VISIBLE
                            }
                        }

                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            super.onAdFailedToLoad(error)
                            if (!isDestroyed && !isFinishing) {
                                if (adView.responseInfo == null) {
                                    frameLayout.visibility = View.GONE
                                    shimmerFrameLayout.visibility = View.GONE
                                }
                            }
                        }
                    }
                    adView.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
                } else {
                    frameLayout.visibility = View.GONE
                    shimmerFrameLayout.visibility = View.GONE
                }
            } else {
                frameLayout.visibility = View.GONE
                shimmerFrameLayout.visibility = View.GONE
                frameLayout.removeAllViews()
            }
        } else {
            binding.llComposeBar.visibility = View.VISIBLE
            binding.llReadOnlyBar.visibility = View.GONE
        }
    }

    private fun launchCamera() {
        try {
            val imagesDir = java.io.File(cacheDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val imageFile = java.io.File(imagesDir, "camera_image_${System.currentTimeMillis()}.jpg")
            tempCameraUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            takePictureLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open camera", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupSelectionMode() {
        adapter.onLongClick = { msg ->
            if (!adapter.isInSelectionMode()) {
                AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MESSAGE_INFO, EventScreenName.CHAT)
                adapter.setSelectionMode(true)
                adapter.toggleSelection(msg.id)
                updateSelectionToolbar()
            }
        }

        adapter.onSelectionChanged = { count ->
            if (count == 0) {
                exitSelectionMode()
            } else {
                binding.tvSelectionCount.text = getString(R.string.extracted_str_0_selected).replace("0", count.toString())
            }
        }

        binding.ivCloseSelection.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.CHAT)
            exitSelectionMode()
        }

        binding.ivSelectionCopy.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
            copySelectedMessages()
            exitSelectionMode()
        }

        binding.ivSelectionDelete.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_MENU, EventScreenName.CHAT)
            deleteSelectedMessages()
        }
    }

    private fun updateSelectionToolbar() {
        val isInSelection = adapter.isInSelectionMode()
        binding.toolbarSelection.visibility = if (isInSelection) View.VISIBLE else View.GONE
        binding.toolbar.visibility = if (isInSelection) View.INVISIBLE else View.VISIBLE
        binding.tvSelectionCount.text = getString(R.string.extracted_str_0_selected).replace("0", adapter.getSelectedCount().toString())
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        updateSelectionToolbar()
    }

    private fun copySelectedMessages() {
        val selectedIds = adapter.getSelectedIds()
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = mutableListOf<String>()
            selectedIds.forEach { id ->
                db.messageDao().getMessageByIdSync(id)?.let {
                    messages.add(it.body)
                }
            }
            
            if (messages.isNotEmpty()) {
                val copyText = messages.joinToString("\n")
                lifecycleScope.launch(Dispatchers.Main) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Messages", copyText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@ChatActivity,
                        getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteSelectedMessages() {
        GeneralDialogs.showStyledDeleteDialog(
            this,
            getString(R.string.are_you_sure_you_want_to_delete)
        ) {
            val selectedIds = adapter.getSelectedIds()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Delete from system provider
                    selectedIds.forEach { id ->
                        try {
                            val msg = db.messageDao().getMessageByIdSync(id)
                            if (msg != null) {
                                // If it's an image, delete from content://mms
                                val uri = if (!msg.imageUri.isNullOrEmpty()) {
                                    Uri.parse("content://mms/${msg.id}")
                                } else {
                                    Uri.parse("content://sms/${msg.id}")
                                }
                                contentResolver.delete(uri, null, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2. Delete from local DB & cleanup scheduling
                    selectedIds.forEach { id ->
                        try {
                            val scheduled = db.scheduledMessageDao().getByLocalIdSync(id)
                            if (scheduled != null) {
                                // Cancel system alarm
                                val intent = Intent(this@ChatActivity, app.messages.receiver.ScheduleReceiver::class.java)
                                val pendingIntent = PendingIntent.getBroadcast(
                                    this@ChatActivity,
                                    scheduled.id.toInt(),
                                    intent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).cancel(pendingIntent)
                                
                                // Delete from scheduled table
                                db.scheduledMessageDao().deleteById(scheduled.id)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    db.messageDao().deleteByIds(selectedIds)

                    lifecycleScope.launch(Dispatchers.Main) {
                        exitSelectionMode()
                        Toast.makeText(this@ChatActivity,
                            getString(R.string.messages_deleted), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    }


    private fun onEmojiClicked() {
        AnalyticsHelper.logEvent(EventNames.CHAT_CLICK_ATTACHMENT, EventScreenName.CHAT)
        toggleEmojiPicker()
    }

    private fun setupEmojiPicker() {
        binding.emojiPickerView.setOnEmojiPickedListener { item ->
            val emoji = item.emoji
            val cursorPosition = binding.etMessage.selectionStart
            if (cursorPosition >= 0) {
                binding.etMessage.text.insert(cursorPosition, emoji)
            } else {
                binding.etMessage.append(emoji)
            }
        }

        binding.etMessage.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {

                val editText = v as android.widget.EditText

                // drawableEnd = index 2
                val drawableEnd = editText.compoundDrawablesRelative[2]

                if (drawableEnd != null) {
                    val drawableWidth = drawableEnd.bounds.width()

                    // Check if touch is inside drawable area
                    if (event.rawX >= (editText.right - drawableWidth - editText.paddingEnd)) {

                        // 👉 CLICK DETECTED
                        onEmojiClicked()

                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        binding.etMessage.setOnClickListener {
            if (binding.emojiPickerView.visibility == View.VISIBLE) {
                binding.emojiPickerView.visibility = View.GONE
            }
            if (binding.llAttachment.visibility == View.VISIBLE) {
                binding.llAttachment.visibility = View.GONE
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
    }

    private fun showKeyboard() {
        binding.etMessage.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleEmojiPicker() {
        if (binding.emojiPickerView.visibility == View.VISIBLE) {
            binding.emojiPickerView.visibility = View.GONE
            showKeyboard()
        } else {
            hideKeyboard()
            binding.llAttachment.visibility = View.GONE
            // Delay slightly to allow keyboard to hide before showing picker to avoid jumpiness
            binding.root.postDelayed({
                binding.emojiPickerView.visibility = View.VISIBLE
            }, 100)
        }
    }

    /**
     * Updates the SIM badge button in the toolbar.
     * - Hidden on single-SIM devices.
     * - Shows "SIM 1" / "SIM 2" etc. when a SIM is selected on multi-SIM devices.
     * - Shows "SIM ?" when no SIM is chosen yet (prompts user to pick).
     */
    private fun updateSimButton() {
        val sims = simManager.getAvailableSims()
        if (sims.size <= 1) {
            binding.btnSimSelection.visibility = View.GONE
            return
        }
        binding.btnSimSelection.visibility = View.VISIBLE
        val label = selectedSim?.let { "SIM ${it.slotIndex + 1}" } ?: "SIM ?"
        binding.btnSimSelection.text = label

    }


    private var lastListSize = 0
    private fun setupDataObservation() {
        observationJob?.cancel()
        observationJob = lifecycleScope.launch {
            db.messageDao().getMessagesByAddressOrThreadFlow(address, threadId).collectLatest { list ->
                val sorted = list.sortedWith(compareBy({ it.date }, { it.id }))
                val chatItems = formatChatItems(sorted)
                
                adapter.submitList(chatItems) {
                    if (chatItems.isNotEmpty()) {
                        binding.rvMessages.scrollToPosition(chatItems.size - 1)
                    }
                    lastListSize = chatItems.size
                }
                
                updateHeaderTitleAndSubtitle()
            }
        }
    }

    private fun formatChatItems(messages: List<MessageEntity>): List<ChatItem> {
        val chatItems = mutableListOf<ChatItem>()
        var lastDate: String? = null
        val seenContent = mutableSetOf<String>()

        // 1. Normalize, clean carrier/anti-spam suffixes, and deduplicate by content/time to handle bridge fragments
        val normalized = messages
            .map { 
                it.copy(
                    date = normalizeTime(it.date),
                    body = app.messages.utility.AddressUtils.cleanMessageBody(it.body)
                ) 
            }
            .sortedWith(compareBy({ it.date }, { it.id }))
            .filter { msg ->
                val key = if (msg.type == 2) {
                    "SENT_${msg.body}_${msg.date / 3000}"
                } else {
                    "${msg.address}_${msg.body}_${msg.date}"
                }
                if (seenContent.contains(key)) return@filter false
                seenContent.add(key)
                true
            }

        normalized.forEach { msg ->
            val dateLabel = getChatDateLabel(msg.date)
            if (dateLabel != lastDate) {
                chatItems.add(ChatItem.DateHeader(dateLabel))
                lastDate = dateLabel
            }
            chatItems.add(ChatItem.Message(msg))
        }
        return chatItems
    }

    private fun normalizeTime(time: Long): Long {
        // If time is in seconds (10 digits), convert to milliseconds (13 digits)
        return if (time < 10_000_000_000L) time * 1000L else time
    }

    private fun getChatDateLabel(timeMillis: Long): String {
        val normalized = normalizeTime(timeMillis)
        val calendar = java.util.Calendar.getInstance()
        
        // Reset to midnight for current day comparison
        val nowCal = java.util.Calendar.getInstance()
        nowCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        nowCal.set(java.util.Calendar.MINUTE, 0)
        nowCal.set(java.util.Calendar.SECOND, 0)
        nowCal.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = nowCal.timeInMillis

        // Yesterday
        nowCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = nowCal.timeInMillis

        return when {
            normalized >= todayStart -> getString(R.string.today)
            normalized >= yesterdayStart -> getString(R.string.yesterday)
            else -> {
                val sdf = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                sdf.format(Date(normalized))
            }
        }
    }

    private val smsRoleLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        if (isDefaultSmsApp()) {
            triggerSync()
        }
    }

    private fun triggerSync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            delay(100) // short debounce to batch rapid calls
            val effectiveThreadId = if (threadId != -1L) {
                threadId
            } else if (address.isNotBlank()) {
                // Try to resolve a system thread ID on the fly
                withContext(Dispatchers.IO) {
                    try {
                        android.provider.Telephony.Threads.getOrCreateThreadId(this@ChatActivity, address)
                    } catch (e: Exception) { -1L }
                }
            } else {
                -1L
            }

            if (effectiveThreadId != -1L) {
                if (threadId == -1L) {
                    threadId = effectiveThreadId
                    app.messages.Controller.activeThreadId = threadId
                    setupDataObservation()
                }
                syncManager.syncMessagesForThread(effectiveThreadId)
                syncManager.markThreadAsRead(effectiveThreadId)
                app.messages.utility.NotificationHelper.clearSession(effectiveThreadId)
                androidx.core.app.NotificationManagerCompat.from(this@ChatActivity).cancel(effectiveThreadId.toInt())
            }
        }
    }

    private val SENT_ACTION = app.messages.receiver.MessageStatusReceiver.SENT_INTENT_ACTION
    private val DELIVERY_ACTION = app.messages.receiver.MessageStatusReceiver.DELIVERY_INTENT_ACTION

    private val sentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == SENT_ACTION) {
                val id = intent.getLongExtra("message_id", -1L)
                val systemUriString = intent.getStringExtra("system_uri")
                if (id != -1L) {
                    val isSuccess = resultCode == android.app.Activity.RESULT_OK
                    val status = if (isSuccess) 2 else 3

                    lifecycleScope.launch {
                        db.messageDao().updateStatus(id, status)

                        // Also update system provider status
                        systemUriString?.let { uriStr ->
                            try {
                                val uri = Uri.parse(uriStr)
                                val values = ContentValues().apply {
                                    put(
                                        Telephony.Sms.TYPE,
                                        if (isSuccess) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
                                    )
                                }
                                contentResolver.update(uri, values, null, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } else if (intent?.action == DELIVERY_ACTION) {
                val id = intent.getLongExtra("message_id", -1L)
                if (id != -1L && resultCode == android.app.Activity.RESULT_OK) {
                    lifecycleScope.launch {
                        db.messageDao().updateDeliveryStatus(id, 6, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Controller.activeThreadId = threadId

        // Trigger sync regardless — triggerSync() now handles the threadId == -1 case
        triggerSync()

        if (threadId != -1L) {
            lifecycleScope.launch {
                syncManager.markThreadAsRead(threadId)
                app.messages.utility.NotificationHelper.clearSession(threadId)
                androidx.core.app.NotificationManagerCompat.from(this@ChatActivity).cancel(threadId.toInt())
            }

            // Fetch dynamic group name changes
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val conversation = db.conversationDao().getById(threadId)
                if (conversation != null) {
                    val newName = conversation.recipientName
                    val newIsSaved = conversation.isSaved
                    val newPhoto = conversation.photoUri
                    if (newName != recipientName || newIsSaved != isSaved || newPhoto != photoUri) {
                        recipientName = newName
                        isSaved = newIsSaved
                        photoUri = newPhoto
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            binding.tvName.text = recipientName ?: address
                            bindHeaderAvatars()
                        }
                    }
                }
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction(SENT_ACTION)
            addAction(DELIVERY_ACTION)
        }
        ContextCompat.registerReceiver(
            this,
            sentReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun showDateTimePicker() {
        val calendar = java.util.Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(java.util.Calendar.YEAR, year)
                calendar.set(java.util.Calendar.MONTH, month)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker(calendar)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val today = java.util.Calendar.getInstance()
        today.set(java.util.Calendar.HOUR_OF_DAY, 0)
        today.set(java.util.Calendar.MINUTE, 0)
        today.set(java.util.Calendar.SECOND, 0)
        today.set(java.util.Calendar.MILLISECOND, 0)
        datePickerDialog.datePicker.minDate = today.timeInMillis - 1000

        datePickerDialog.setOnCancelListener {
            if (scheduledTimestamp == 0L) {
                isScheduling = false
            }
        }
        datePickerDialog.show()
    }

    private fun showTimePicker(calendar: java.util.Calendar) {
        val timePickerDialog = android.app.TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)

                if (calendar.timeInMillis < System.currentTimeMillis() - 60000) {
                    Toast.makeText(this, "Please select a future time", Toast.LENGTH_SHORT).show()
                    showDateTimePicker()
                } else {
                    scheduledTimestamp = calendar.timeInMillis
                    isScheduling = true
                    binding.layoutScheduledIndicator.visibility = View.VISIBLE
                    binding.dividerScheduleMessage.visibility = View.VISIBLE
                    val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                    binding.tvScheduledTime.text = "Scheduled for " + sdf.format(java.util.Date(scheduledTimestamp))
                }
            },
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            false
        )
        timePickerDialog.setOnCancelListener {
            if (scheduledTimestamp == 0L) {
                isScheduling = false
            }
        }
        timePickerDialog.show()
    }

    private fun updateSendButtonState() {
        val text = binding.etMessage.text.toString()
        if (text.isNotBlank() || tempImageUri != null) {
            binding.btnSend.isEnabled = true
            binding.btnSend.backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@ChatActivity,
                        R.color.colorPrimary
                    )
                )
        } else {
            binding.btnSend.isEnabled = false
            binding.btnSend.backgroundTintList =
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@ChatActivity,
                        R.color.colorPrimary
                    )
                )
        }
    }

    override fun onPause() {
        super.onPause()
        
        // Save Draft
        val draftKey = if (threadId != -1L) threadId.toString() else address
        if (draftKey.isNotBlank()) {
            val currentText = binding.etMessage.text.toString()
            app.messages.utility.DraftManager.saveDraft(this, draftKey, currentText)
        }
        
        Controller.activeThreadId = -1L
        unregisterReceiver(sentReceiver)
        contentResolver.unregisterContentObserver(contentObserver)
    }

    private fun clearInputFields() {
        binding.etMessage.text.clear()
        hideImagePreview()
    }

    private fun sendMessage(body: String, imageUri: String? = null) {
        if (address.isBlank()) {
            Toast.makeText(this, "Invalid address", Toast.LENGTH_SHORT).show()
            return
        }
        
        val draftKey = if (threadId != -1L) threadId.toString() else address
        app.messages.utility.DraftManager.saveDraft(this, draftKey, "")

        // Apply "Strip Accents" transformation if the setting is enabled
        val processedBody = if (PreferenceHelper.getInstance(this).getStripAccentsEnabled()) {
            stripAccents(body)
        } else {
            body
        }

        if (isScheduling && scheduledTimestamp != 0L) {
            scheduleMessage(processedBody, imageUri)
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                getString(R.string.permission_required_to_send_sms), Toast.LENGTH_SHORT
            ).show()
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 101)
            return
        }

        // Ensure Thread ID is resolved before sending
        if (threadId == -1L) {
            val now = System.currentTimeMillis()
            threadId = -1L * kotlin.math.abs(address.hashCode().toLong())
            if (threadId == 0L) threadId = -1L

            val newConversation = app.messages.data.db.ConversationEntity(
                threadId = threadId,
                address = address,
                body = if (processedBody.isBlank() && imageUri != null) "📷 Media message" else processedBody,
                date = now,
                read = true,
                messageCount = 0, // Will be incremented or synced
                recipientName = recipientName,
                photoUri = photoUri,
                lastMsgType = 2,
                isSaved = isSaved,
                normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
            )
            app.messages.Controller.activeThreadId = threadId
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.conversationDao().insert(newConversation)
                }
                setupDataObservation()
                
                // Now proceed with sending
                proceedWithSending(processedBody, imageUri)
            }
            return
        }

        proceedWithSending(processedBody, imageUri)
    }

    private fun proceedWithSending(body: String, imageUri: String?) {
        // Hide attachment layout if visible
        binding.llAttachment.visibility = View.GONE

        // Check if we have multiple SIMs available
        val availableSims = simManager.getAvailableSims()
        if (availableSims.size > 1 && selectedSim == null) {
            // Show SIM selection dialog
            showSimSelectionDialog(availableSims, body, imageUri, true)
            return
        }

        if (imageUri != null) {
            sendDelayedOrActual(address, body, Uri.parse(imageUri), selectedSim)
            clearInputFields()
            return
        }

        sendDelayedOrActual(address, body, null, selectedSim)
        clearInputFields()
    }

    private fun sendDelayedOrActual(address: String, body: String, imageUri: Uri?, sim: SimInfo?) {
        val delaySec = PreferenceHelper.getInstance(this).getDelayedSendingInSeconds()
        if (delaySec > 0) {
            startDelayedSend(address, body, imageUri, sim, delaySec)
        } else {
            if (imageUri != null) {
                sendMmsNative(address, body, imageUri)
            } else if (sim != null) {
                sendSmsWithSim(sim, body)
            } else {
                sendSms(address, body)
            }
        }
    }

    private fun startDelayedSend(address: String, body: String, imageUri: Uri?, sim: SimInfo?, delaySec: Int) {
        val now = System.currentTimeMillis()
        val scheduledTime = now + (delaySec * 1000L)
        val msgId = now

        val localMsg = MessageEntity(
            id = msgId,
            threadId = threadId,
            address = address,
            body = body,
            date = now,
            type = 2, // Sent
            read = true,
            status = 5, // Delaying
            imageUri = imageUri?.toString(),
            simSlot = sim?.slotIndex,
            subscriptionId = sim?.subscriptionId,
            scheduledTime = scheduledTime
        )

        val scheduledMsg = app.messages.data.db.LauncherScheduledMessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            scheduledTime = scheduledTime,
            status = 0, // Pending
            subscriptionId = sim?.subscriptionId,
            simSlot = sim?.slotIndex,
            localMessageId = msgId,
            imageUri = imageUri?.toString()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.messageDao().insert(localMsg)
            val id = db.scheduledMessageDao().insert(scheduledMsg)

            // Set Alarm
            val intent = Intent(this@ChatActivity, app.messages.receiver.ScheduleReceiver::class.java).apply {
                putExtra("scheduled_id", id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this@ChatActivity, id.toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, scheduledTime, pendingIntent)
            }
        }
    }

    private fun cancelDelayedMessage(msg: MessageEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val scheduled = db.scheduledMessageDao().getByLocalMessageIdSync(msg.id)
            if (scheduled != null) {
                // Cancel Alarm
                val intent = Intent(this@ChatActivity, app.messages.receiver.ScheduleReceiver::class.java).apply {
                    putExtra("scheduled_id", scheduled.id)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this@ChatActivity, scheduled.id.toInt(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                alarmManager.cancel(pendingIntent)

                // Delete from DB
                db.scheduledMessageDao().deleteById(scheduled.id)
                db.messageDao().deleteByIds(listOf(msg.id))
            }
        }
    }


    private fun showSimSelectionDialog(
        sims: List<SimInfo>,
        body: String?,
        imageUri: String?,
        forSending: Boolean = false
    ) {
        val dialog = SimSelectionDialog(sims, selectedSim?.subscriptionId) { selectedSim ->
            this.selectedSim = selectedSim  // Override system default for this session
            updateSimButton()
            if (forSending) {
                // Send message using the selected SIM
                if (!body.isNullOrEmpty() || imageUri != null) {
                    sendDelayedOrActual(address, body ?: "", if (imageUri != null) Uri.parse(imageUri) else null, selectedSim)
                    clearInputFields()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.cannot_send_empty_message), Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // Just store the selected SIM for future use
                Toast.makeText(
                    this,
                    "SIM ${selectedSim.slotIndex + 1} selected for sending",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        dialog.show(supportFragmentManager, "SimSelectionDialog")
    }

    private fun sendSmsWithSim(simInfo: SimInfo, body: String) {
        val now = System.currentTimeMillis()
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    getString(R.string.sms_permission_required_to_send_messages), Toast.LENGTH_SHORT
                )
                    .show()
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 101)
                return
            }

            // getSmsManagerForSubscriptionId is the correct API (API 22+) to send via a specific SIM.
            // The old reflection approach was silently falling back to SIM 1 on most devices.
            @Suppress("DEPRECATION")
            val smsManager =
                SmsManager.getSmsManagerForSubscriptionId(simInfo.subscriptionId.toInt())


            val recipients = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val primaryRecipient = recipients.firstOrNull() ?: address

            // Write to system provider so the conversation is visible in other SMS apps as OUTBOX / SENT
            val systemUri = writeToSystemProvider(
                primaryRecipient,
                body,
                threadId,
                now,
                Telephony.Sms.MESSAGE_TYPE_SENT
            )

            // Extract the actual System ID from the returned URI (e.g., content://sms/123)
            val systemId = systemUri?.lastPathSegment?.toLongOrNull() ?: now
            val msgId = systemId

            val localMsg = MessageEntity(
                id = msgId,
                threadId = threadId,
                address = address,
                body = body,
                date = now,
                type = 2,   // Sent
                read = true,
                status = 1, // Sending
                imageUri = null,
                simSlot = simInfo.slotIndex,
                subscriptionId = simInfo.subscriptionId
            )
            lifecycleScope.launch { 
                db.messageDao().insert(localMsg)
                val existing = db.conversationDao().getById(threadId)
                val updatedConv = ConversationEntity(
                    threadId = threadId,
                    address = address,
                    body = AddressUtils.cleanMessageBody(body),
                    date = now,
                    read = true,
                    messageCount = (existing?.messageCount ?: 0) + 1,
                    recipientName = existing?.recipientName ?: recipientName,
                    photoUri = existing?.photoUri ?: photoUri,
                    lastMsgType = 2, // Sent
                    isSaved = existing?.isSaved ?: isSaved,
                    isPinned = existing?.isPinned ?: false,
                    normalizedAddress = AddressUtils.normalizeAddress(address)
                )
                db.conversationDao().insert(updatedConv)
            }

            val deliveryReportsEnabled = PreferenceHelper.getInstance(this).getDeliveryReportsEnabled()

            val sentIntent = android.content.Intent(SENT_ACTION).apply {
                putExtra("message_id", msgId)
                putExtra("system_uri", systemUri?.toString())
                setPackage(packageName)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentPI = PendingIntent.getBroadcast(
                this,
                msgId.toInt(),
                sentIntent,
                pendingIntentFlags
            )

            val deliveryPI = if (deliveryReportsEnabled) {
                val deliveryIntent = android.content.Intent(DELIVERY_ACTION).apply {
                    putExtra("message_id", msgId)
                    setPackage(packageName)
                }
                PendingIntent.getBroadcast(
                    this,
                    msgId.toInt() + 1,
                    deliveryIntent,
                    pendingIntentFlags
                )
            } else null

            val parts = smsManager.divideMessage(body)
            for (recipient in recipients) {
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(sentPI) }
                    }
                    val deliveryIntents = if (deliveryPI != null) {
                        ArrayList<PendingIntent>().apply { repeat(parts.size) { add(deliveryPI) } }
                    } else null
                    
                    smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveryIntents)
                } else {
                    smsManager.sendTextMessage(recipient, null, body, sentPI, deliveryPI)
                }
            }

            if (simManager.getAvailableSims().size > 1) {
                Toast.makeText(
                    this,
                    "${getString(R.string.message_sending_via_sim)} ${simInfo.slotIndex + 1} (${simInfo.carrierName})",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { db.messageDao().updateStatus(now, 3) }
        }
    }

    private fun sendSms(address: String, body: String) {
        val now = System.currentTimeMillis()
        try {
            // Check if we have SEND_SMS permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "SMS permission required to send messages", Toast.LENGTH_SHORT)
                    .show()
                requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 101)
                return
            }

            val smsManager =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }


            val recipients = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val primaryRecipient = recipients.firstOrNull() ?: address

            // Write to system provider first as OUTBOX / SENT to get a real System ID
            val systemUri = writeToSystemProvider(
                primaryRecipient,
                body,
                threadId,
                now,
                Telephony.Sms.MESSAGE_TYPE_SENT
            )

            // Extract the actual System ID from the returned URI (e.g., content://sms/123)
            val systemId = systemUri?.lastPathSegment?.toLongOrNull() ?: now
            val msgId = systemId

            // Relying on SyncManager and Telephony ContentObserver to update the DB
            val localMsg = MessageEntity(
                id = msgId,
                threadId = threadId,
                address = address,
                body = body,
                date = now,
                type = 2, // Sent
                read = true,
                status = 1, // Sending
                imageUri = null
            )

            lifecycleScope.launch {
                db.messageDao().insert(localMsg)
                val existing = db.conversationDao().getById(threadId)
                val updatedConv = ConversationEntity(
                    threadId = threadId,
                    address = address,
                    body = app.messages.utility.AddressUtils.cleanMessageBody(body),
                    date = now,
                    read = true,
                    messageCount = (existing?.messageCount ?: 0) + 1,
                    recipientName = existing?.recipientName ?: recipientName,
                    photoUri = existing?.photoUri ?: photoUri,
                    lastMsgType = 2, // Sent
                    isSaved = existing?.isSaved ?: isSaved,
                    isPinned = existing?.isPinned ?: false,
                    normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
                )
                db.conversationDao().insert(updatedConv)
            }

            val deliveryReportsEnabled = PreferenceHelper.getInstance(this).getDeliveryReportsEnabled()

            val sentIntent = android.content.Intent(SENT_ACTION).apply {
                putExtra("message_id", msgId)
                putExtra("system_uri", systemUri?.toString())
                setPackage(packageName)
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentPI = PendingIntent.getBroadcast(
                this,
                msgId.toInt(),
                sentIntent,
                pendingIntentFlags
            )

            val deliveryPI = if (deliveryReportsEnabled) {
                val deliveryIntent = android.content.Intent(DELIVERY_ACTION).apply {
                    putExtra("message_id", msgId)
                    setPackage(packageName)
                }
                PendingIntent.getBroadcast(
                    this,
                    msgId.toInt() + 1,
                    deliveryIntent,
                    pendingIntentFlags
                )
            } else null

            val parts = smsManager.divideMessage(body)
            for (recipient in recipients) {
                if (parts.size > 1) {
                    val sentIntents = ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(sentPI) }
                    }
                    val deliveryIntents = if (deliveryPI != null) {
                        ArrayList<PendingIntent>().apply { repeat(parts.size) { add(deliveryPI) } }
                    } else null
                    
                    smsManager.sendMultipartTextMessage(recipient, null, parts, sentIntents, deliveryIntents)
                } else {
                    smsManager.sendTextMessage(recipient, null, body, sentPI, deliveryPI)
                }
            }

            Toast.makeText(this, "Sending message...", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(
                this,
                "Permission denied. Please grant SMS permission to send messages.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { db.messageDao().updateStatus(now, 3) }
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
        } else {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            packageName == defaultSmsPackage
        }
    }

    private fun promptSetDefaultSmsApp() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_as_default_sms_app))
            .setMessage(getString(R.string.to_send_sms_messages_this_app_needs_to_be_set_as_your_default_sms_app_would_you_like_to_do_that_now))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                        smsRoleLauncher.launch(intent)
                    } else {
                        launchLegacyDefaultSmsRequest()
                    }
                } else {
                    launchLegacyDefaultSmsRequest()
                }
            }
            .setNegativeButton(getString(R.string.no)) { _, _ ->
                Toast.makeText(
                    this,
                    getString(R.string.cannot_send_messages_without_being_the_default_sms_app),
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun launchLegacyDefaultSmsRequest() {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        smsRoleLauncher.launch(intent)
    }

    private fun sendMmsNative(address: String, body: String, imageUri: Uri) {
        // We use a mock ID for tracking since we're sending natively
        val msgId = System.currentTimeMillis()

        // Create local message record as SENDING immediately so UI updates instantly
        val localMsg = MessageEntity(
            id = msgId,
            threadId = threadId,
            address = address,
            body = body,
            date = msgId,
            type = 2, // Sent
            read = true,
            status = 1, // Sending
            imageUri = imageUri.toString()
        )

        lifecycleScope.launch {
            db.messageDao().insert(localMsg)
        }

        // Process and send the image in the background
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = Settings()
            settings.setUseSystemSending(true)
            settings.setDeliveryReports(false)
            selectedSim?.subscriptionId?.let { subId ->
                settings.setSubscriptionId(subId.toInt())
            }

            val message = Message(body, address)

            // Convert Uri to Bitmap for the library
            try {
                val inputStream = contentResolver.openInputStream(imageUri)
                var bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val limitKb = app.messages.preferences.PreferenceHelper.getInstance(this@ChatActivity).getMmsCompressionLimit()
                val targetBytes = if (limitKb == -1) 300 * 1024 else if (limitKb == 0) -1 else limitKb * 1024
                
                // Reserve 20KB for MMS PDU overhead (headers, SMIL, text parts, etc.)
                // If we don't reserve this, the total payload exceeds the carrier's strict limit and the attachment gets dropped or corrupted.
                val maxImageBytes = if (targetBytes != -1) targetBytes - (20 * 1024) else -1
                
                var finalImageBytes: ByteArray? = null

                if (bitmap != null && maxImageBytes > 0) {
                    var stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
                    
                    if (stream.toByteArray().size > maxImageBytes) {
                        var scale = 0.9f
                        while (scale > 0.1f) {
                            stream = java.io.ByteArrayOutputStream()
                            val scaledWidth = (bitmap.width * scale).toInt()
                            val scaledHeight = (bitmap.height * scale).toInt()
                            if (scaledWidth <= 0 || scaledHeight <= 0) break
                            
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                            
                            if (stream.toByteArray().size <= maxImageBytes) {
                                bitmap = scaledBitmap
                                break
                            }
                            if (scale <= 0.11f) {
                                bitmap = scaledBitmap
                                break
                            }
                            scale -= 0.1f
                        }
                    }
                    finalImageBytes = stream.toByteArray()
                }

                if (bitmap != null) {
                    if (finalImageBytes != null && finalImageBytes.isNotEmpty()) {
                        // Use the precise byte array we generated to prevent Klinker from re-compressing at 90% quality and breaking the size limit
                        message.addMedia(finalImageBytes, "image/jpeg")
                    } else {
                        // Fallback if no compression was configured
                        message.addImage(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "Failed to decode image", Toast.LENGTH_SHORT).show()
                    }
                    db.messageDao().updateStatus(msgId, 3) // Failed
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Failed to attach image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                db.messageDao().updateStatus(msgId, 3) // Failed
                return@launch
            }

            // Create transaction and send message
            try {
                val transaction = Transaction(this@ChatActivity, settings)
                transaction.sendNewMessage(message, msgId)

                // The library sends a broadcast when finished.
                // For now, we manually mark as sent to provide immediate feedback.
                db.messageDao().updateStatus(msgId, 2) // Sent
            } catch (e: Exception) {
                e.printStackTrace()
                db.messageDao().updateStatus(msgId, 3) // Failed
            }
        }
    }


    private fun writeToSystemProvider(
        address: String,
        body: String,
        threadId: Long,
        timestamp: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_SENT
    ): Uri? {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, type)
                if (threadId > 0) {
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
            }
            return contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun scheduleMessage(body: String, imageUri: String?) {
        val now = System.currentTimeMillis()
        if (threadId == -1L) {
            threadId = -1L * kotlin.math.abs(address.hashCode().toLong())
            if (threadId == 0L) threadId = -1L
        }

        // Safety: Ensure time is in the future. If user took too long to pick, offset it slightly.
        val finalScheduledTime = if (scheduledTimestamp <= now) now + 5000 else scheduledTimestamp

        val localMsgId = System.currentTimeMillis()
        val localMsg = app.messages.data.db.MessageEntity(
            id = localMsgId,
            threadId = threadId,
            address = address,
            body = body,
            date = now,
            type = 2, // Sent
            read = true,
            status = 4, // Scheduled
            imageUri = imageUri,
            subscriptionId = selectedSim?.subscriptionId,
            simSlot = selectedSim?.slotIndex,
            scheduledTime = finalScheduledTime
        )

        val scheduledMsg = app.messages.data.db.LauncherScheduledMessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            scheduledTime = finalScheduledTime,
            status = 0, // Pending
            subscriptionId = selectedSim?.subscriptionId,
            simSlot = selectedSim?.slotIndex,
            localMessageId = localMsgId
        )

        lifecycleScope.launch {
            var id = 0L
            withContext(Dispatchers.IO) {
                // Ensure this conversation shows up on the Home screen
                val existingConv = db.conversationDao().getByIdSync(threadId)
                if (existingConv == null) {
                    val newConversation = app.messages.data.db.ConversationEntity(
                        threadId = threadId,
                        address = address,
                        body = body,
                        date = now,
                        read = true,
                        messageCount = 1,
                        recipientName = recipientName,
                        photoUri = photoUri,
                        lastMsgType = 2,
                        isSaved = isSaved,
                        normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
                    )
                    db.conversationDao().insert(newConversation)
                } else {
                    db.conversationDao().insert(existingConv.copy(body = body, date = now))
                }
                
                db.messageDao().insert(localMsg)
                id = db.scheduledMessageDao().insert(scheduledMsg)
            }
            
            // Re-fetch everything to show the scheduled message placeholder
            setupDataObservation()

            // Hide scheduling UI aspects
            binding.llAttachment.visibility = View.GONE
            clearInputFields()
            isScheduling = false
            scheduledTimestamp = 0L
            binding.layoutScheduledIndicator.visibility = View.GONE
            binding.dividerScheduleMessage.visibility = View.GONE

            // Set Alarm
            val intent = Intent(
                this@ChatActivity,
                app.messages.receiver.ScheduleReceiver::class.java
            ).apply {
                putExtra("scheduled_id", id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this@ChatActivity,
                id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, finalScheduledTime, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, finalScheduledTime, pendingIntent)
            }

            Toast.makeText(this@ChatActivity, "Message scheduled", Toast.LENGTH_SHORT).show()
            clearInputFields()
            isScheduling = false
            scheduledTimestamp = 0L
            binding.layoutScheduledIndicator.visibility = View.GONE
            binding.dividerScheduleMessage.visibility = View.GONE

            // If we came from ScheduleActivity, we might want to finish
            if (intent.getBooleanExtra("is_scheduling", false)) {
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        getString(R.string.sms_permission_granted), Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.sms_permission_denied_cannot_send_messages),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Removes accent/diacritical marks from a string using Unicode NFD normalization.
     * "Café mañana" → "Cafe manana"
     * This ensures messages stay within GSM-7 encoding and avoids extra SMS parts.
     */
    private fun stripAccents(input: String): String {
        if (input.isEmpty()) return input
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    override fun onBackPressed() {
        if (::adapter.isInitialized && adapter.isInSelectionMode()) {
            exitSelectionMode()
        } else {
            finish()
//            app.messages.config.AdsConfigManager.onBackClicked(this) {
//                super.onBackPressed()
//            }
        }
    }
    private fun bindHeaderAvatars() {
        val isGroup = address.contains(",")
        val contactsMap = app.messages.data.CacheManager.getInstance(this).getMemoryCachedContactsMap()

        // Reset visibility
        binding.ivAvatarFull.visibility = View.GONE
        binding.tvInitialsFull.visibility = View.GONE
        binding.flAvatarBack.visibility = View.GONE
        binding.flAvatarFront.visibility = View.GONE

        if (isGroup) {
            val addresses = address.split(",").filter { it.isNotBlank() }
            if (addresses.isNotEmpty()) {
                binding.flAvatarBack.visibility = View.VISIBLE
                bindCircleAvatar(addresses[0], binding.ivAvatarBack, binding.tvInitialsBack, contactsMap)

                if (addresses.size > 1) {
                    binding.flAvatarFront.visibility = View.VISIBLE
                    bindCircleAvatar(addresses[1], binding.ivAvatarFront, binding.tvInitialsFront, contactsMap)
                }
            } else {
                binding.ivAvatarFull.visibility = View.VISIBLE
                binding.ivAvatarFull.setImageResource(R.drawable.ic_group)
                binding.ivAvatarFull.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black))
            }
        } else {
            val name = recipientName ?: address
            if (photoUri != null) {
                binding.ivAvatarFull.visibility = View.VISIBLE
                binding.ivAvatarFull.imageTintList = null
                Glide.with(this).load(photoUri).circleCrop().into(binding.ivAvatarFull)
            } else {
                if (isSaved) {
                    binding.tvInitialsFull.visibility = View.VISIBLE
                    binding.tvInitialsFull.text = getInitials(name)
                    applyAvatarColors(binding.tvInitialsFull, binding.ivAvatarFull, name)
                } else {
                    // Short-code or raw number (Company/Unknown)
                    binding.ivAvatarFull.visibility = View.VISIBLE
                    binding.ivAvatarFull.setImageResource(R.drawable.ic_person)
                    binding.ivAvatarFull.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black))
                }
            }
        }
    }

    private fun bindCircleAvatar(
        address: String,
        iv: com.google.android.material.imageview.ShapeableImageView,
        tv: android.widget.TextView,
        contactsMap: Map<String, app.messages.data.model.CachedContactInfo>
    ) {
        val info = contactsMap[address]
            ?: contactsMap[normalizeNumber(address)]
            ?: contactsMap[if (address.length >= 10) address.takeLast(10) else address]

        val photoUri = info?.photoUri
        val name = info?.name ?: address

        iv.visibility = View.VISIBLE
        if (photoUri != null) {
            tv.visibility = View.GONE
            iv.imageTintList = null
            Glide.with(this).load(photoUri).circleCrop().into(iv)
        } else {
            tv.visibility = View.VISIBLE
            iv.setImageDrawable(null)
            tv.text = getInitials(name).take(1)
            applyAvatarColors(tv, iv, name)
        }
    }

    private fun isHumanName(name: String): Boolean {
        if (name.contains(Regex("[a-zA-Z]"))) {
            val digitCount = name.count { it.isDigit() }
            if (digitCount > 4 && name.contains("-")) return false
            return true
        }
        return false
    }

    private fun normalizeNumber(number: String): String {
        return number.filter { it.isDigit() }
    }

    private fun getInitials(name: String): String {
        if (name.isEmpty()) return ""
        val parts = name.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[parts.size - 1].first()}"
        } else {
            parts[0].first().toString()
        }.uppercase()
    }

    private fun applyAvatarColors(tv: android.widget.TextView, iv: com.google.android.material.imageview.ShapeableImageView, name: String) {
        val colors = listOf(
            Pair("#E3F2FD", "#1976D2"), // Blue
            Pair("#FBE9E7", "#D84315"), // Deep Orange
            Pair("#E8F5E9", "#388E3C"), // Green
            Pair("#FCE4EC", "#C2185B"), // Pink
            Pair("#FFF3E0", "#F57C00"), // Orange
            Pair("#F3E5F5", "#7B1FA2")  // Purple
        )
        val initialChar = getInitials(name).firstOrNull() ?: 'A'
        val colorPair = colors[kotlin.math.abs(initialChar.hashCode()) % colors.size]
        
        iv.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorPair.first))
        iv.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorPair.first))
        
        tv.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(colorPair.first))
        tv.setTextColor(android.graphics.Color.parseColor(colorPair.second))
    }
}


