package app.messages.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.db.Contact
import app.messages.databinding.ItemContactBinding
import java.util.Locale

class ContactAdapter(
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val contacts = ArrayList<Contact>()
    private var allContacts = listOf<Contact>() // full data set for filtering

    fun submitList(list: List<Contact>) {
        allContacts = list
        contacts.clear()
        contacts.addAll(list)
        notifyDataSetChanged()
    }

    /**
     * Filter contacts by name or phone number containing [query] (case insensitive).
     */
    fun filter(query: String) {
        if (query.isBlank()) {
            contacts.clear()
            contacts.addAll(allContacts)
        } else {
            val lower = query.trim().lowercase()
            val filtered = allContacts.filter { c ->
                c.name.lowercase().contains(lower) || c.phoneNumber.contains(lower)
            }
            contacts.clear()
            contacts.addAll(filtered)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    inner class ContactViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            val context = itemView.context
            binding.tvName.text = contact.name
            binding.tvNumber.text = contact.phoneNumber

            if (contact.photoUri != null) {
                binding.tvInitials.visibility = View.GONE
                binding.ivAvatar.visibility = View.VISIBLE
                binding.ivAvatar.tag = contact.photoUri
                binding.ivAvatar.imageTintList = null // Clear any tint for actual photos
                
                Glide.with(itemView.context)
                    .load(contact.photoUri)
                    .circleCrop()
                    .dontAnimate()
                    .into(binding.ivAvatar)
            } else {
                val initials = getInitials(contact.name ?: contact.phoneNumber)
                if (initials.isNotEmpty() && !initials[0].isDigit()) {
                    binding.tvInitials.visibility = View.VISIBLE
                    binding.ivAvatar.visibility = View.GONE
                    binding.tvInitials.text = initials
                    // Optional: You can set a unique color based on name hash here
                } else {
                    binding.tvInitials.visibility = View.GONE
                    binding.ivAvatar.visibility = View.VISIBLE
                    binding.ivAvatar.tag = "person_fallback"
                    binding.ivAvatar.setImageResource(R.drawable.ic_person)
                    // Apply tint for placeholder icon
                    binding.ivAvatar.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.black))
                }
            }

            binding.root.setOnClickListener { onClick(contact) }
        }

        private fun getInitials(name: String): String {
            if (name.isEmpty()) return ""
            val parts = name.trim().split(Regex("\\s+"))
            return if (parts.size >= 2) {
                "${parts[0].first()}${parts[parts.size - 1].first()}"
            } else {
                parts[0].first().toString()
            }.uppercase(Locale.getDefault())
        }
    }
}
