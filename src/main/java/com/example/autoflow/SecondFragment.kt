package com.example.autoflow

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import com.example.autoflow.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonBackToDashboard.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        // Check for notification access permission
        if (!isNotificationServiceEnabled()) {
            requestNotificationAccessDialog()
        } else {
            // Optional: Show a toast or log if already enabled
            // Toast.makeText(requireContext(), "Notification access is enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = requireContext().packageName
        // Setting "enabled_notification_listeners" is a colon-separated string of "packageName/className"
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        // Check if your specific NotificationListenerService class is enabled
                        // For now, checking package name is a good first step.
                        // For more robustness, you'd check cn.className against your service's class name.
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun requestNotificationAccessDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Notification Access Needed")
            .setMessage("To automatically record expenses from notifications, this app needs access to your notifications. Please grant access in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                // ACTION_NOTIFICATION_LISTENER_SETTINGS is the correct intent for API 22+
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                // Optionally, you can add your package name as an extra to take the user directly to your app's setting,
                // but this is not officially documented to work on all devices/OS versions.
                // intent.putExtra(":settings:fragment_args_key", requireContext().packageName)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Optionally, explain to the user that the feature won't work without permission
                Toast.makeText(requireContext(), "Notification access is required for automatic expense tracking.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
