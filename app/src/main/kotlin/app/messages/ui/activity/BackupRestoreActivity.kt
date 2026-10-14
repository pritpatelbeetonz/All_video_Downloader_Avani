package app.messages.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import app.messages.R
import app.messages.databinding.ActivityBackupRestoreBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import app.messages.ui.dialogs.GeneralDialogs
import app.messages.utility.BackupUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupRestoreActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupRestoreBinding
    private lateinit var prefs: PreferenceHelper
    
    private var progressDialog: AlertDialog? = null

    private val backupFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.setBackupFolderUri(uri.toString())
            updateBackupFolderUI()
        }
    }

    private val restoreFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val file = DocumentFile.fromSingleUri(this, uri)
            val name = file?.name ?: "Selected Backup"
            confirmAndRestore(uri, name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper.getInstance(this)
        
        setupToolbar()
        initUI()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.incToolbar.tvTitle.text = getString(R.string.backup_and_restore)
        binding.incToolbar.tvDone.visibility = View.GONE
        binding.incToolbar.ivBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun initUI() {
        updateBackupFolderUI()
    }
    
    private fun updateBackupFolderUI() {
        val folderUriStr = prefs.getBackupFolderUri()
        if (folderUriStr != null) {
            try {
                val uri = Uri.parse(folderUriStr)
                val folder = DocumentFile.fromTreeUri(this, uri)
                if (folder != null && folder.canWrite()) {
                    binding.tvBackupFolderPath.text = folder.name ?: uri.path
                } else {
                    binding.tvBackupFolderPath.text = getString(R.string.extracted_backup_folder)
                }
            } catch (e: Exception) {
                binding.tvBackupFolderPath.text = getString(R.string.extracted_backup_folder)
            }
        } else {
            binding.tvBackupFolderPath.text = getString(R.string.extracted_backup_folder)
        }
    }

    private fun setupListeners() {
        binding.cvBackupLocation.setOnClickListener {
            backupFolderLauncher.launch(null)
        }

        binding.btnBackupNow.setOnClickListener {
            val folderUriStr = prefs.getBackupFolderUri()
            if (folderUriStr != null) {
                try {
                    val uri = Uri.parse(folderUriStr)
                    val folder = DocumentFile.fromTreeUri(this, uri)
                    if (folder != null && folder.canWrite()) {
                        startBackup(uri)
                    } else {
                        }
                } catch (e: Exception) {
                    }
            } else {
                }
        }

        binding.rlRestore.setOnClickListener {
            restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }
    
    private fun showProgressDialog(message: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvProgressMessage)
        tvMessage.text = message

        progressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        progressDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        progressDialog?.show()
    }
    
    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun startBackup(folderUri: Uri) {
        lifecycleScope.launch {
            binding.btnBackupNow.isEnabled = false
            showProgressDialog("Creating backup...")

            val result = BackupUtility.createBackup(this@BackupRestoreActivity, folderUri)

            withContext(Dispatchers.Main) {
                hideProgressDialog()
                if (result.isSuccess) {
                    val time = System.currentTimeMillis()
                    prefs.setLastBackupTime(time)
                    initUI()
                    } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    }

                binding.root.postDelayed({
                    binding.btnBackupNow.isEnabled = true
                }, 3000)
            }
        }
    }

    private var pendingRestoreUri: Uri? = null

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingRestoreUri?.let { uri ->
            startRestore(uri)
        }
        pendingRestoreUri = null
    }

    private fun requestDefaultSmsRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager?.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS) == true) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                defaultSmsRoleLauncher.launch(intent)
            } else {
                val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                defaultSmsRoleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsRoleLauncher.launch(intent)
        }
    }

    private fun confirmAndRestore(fileUri: Uri, fileName: String) {
        GeneralDialogs.showConfirmationDialog(
            this,
            getString(R.string.restore_backup),
            getString(
                R.string.are_you_sure_you_want_to_restore_from_this_will_update_your_messages_and_conversations,
                fileName
            ),
            getString(R.string.restore)
        ) {
            if (BackupUtility.isDefaultSmsApp(this)) {
                startRestore(fileUri)
            } else {
                pendingRestoreUri = fileUri
                requestDefaultSmsRole()
            }
        }
    }

    private fun startRestore(fileUri: Uri) {
        lifecycleScope.launch {
            binding.btnBackupNow.isEnabled = false
            showProgressDialog("Restoring messages...")

            val result = BackupUtility.restoreBackup(this@BackupRestoreActivity, fileUri)

            withContext(Dispatchers.Main) {
                hideProgressDialog()
                if (result.isSuccess) {
                    } else {
                    val error = result.exceptionOrNull()?.message ?: getString(R.string.unknown_error)
                    }

                binding.root.postDelayed({
                    binding.btnBackupNow.isEnabled = true
                }, 3000)
            }
        }
    }
}
