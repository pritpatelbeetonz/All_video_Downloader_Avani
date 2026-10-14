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
import app.messages.databinding.ItemAddPeopleBinding
import java.util.Locale

class AddPeopleAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<AddPeopleAdapter.AddPeopleViewHolder>() {

    private val contacts = ArrayList<Contact>()
    private val filteredContacts = ArrayList<Contact>()
    private val selectedContacts = HashSet<String>()

    fun submitList(list: List<Contact>) {
        contacts.clear()
        contacts.addAll(list)
        filteredContacts.clear()
        filteredContacts.addAll(list)
        notifyDataSetChanged()
    }

    fun preSelectContacts(phoneNumbers: List<String>) {
        for (contact in contacts) {
            val normalized = contact.phoneNumber.filter { it.isDigit() }
            // Check if any of the provided numbers match (checking last 10 digits/full match)
            val isMatch = phoneNumbers.any { 
                it == normalized || (it.length >= 10 && normalized.endsWith(it.takeLast(10))) || (normalized.length >= 10 && it.endsWith(normalized.takeLast(10)))
            }
            
            if (isMatch) {
                selectedContacts.add(contact.phoneNumber)
            }
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedContacts.size)
    }

    fun filter(query: String) {
        filteredContacts.clear()
        if (query.isEmpty()) {
            filteredContacts.addAll(contacts)
        } else {
            val lowerCaseQuery = query.lowercase()
            filteredContacts.addAll(contacts.filter { 
                it.name.lowercase().contains(lowerCaseQuery) || it.phoneNumber.contains(query)
            })
        }
        notifyDataSetChanged()
    }

    fun getSelectedCount() = selectedContacts.size

    fun getSelectedContacts(): List<Contact> {
        return contacts.filter { selectedContacts.contains(it.phoneNumber) }
    }

    fun getSelectedPhoneNumbers(): Set<String> {
        return selectedContacts
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddPeopleViewHolder {
        val binding = ItemAddPeopleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AddPeopleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AddPeopleViewHolder, position: Int) {
        holder.bind(filteredContacts[position])
    }

    override fun getItemCount() = filteredContacts.size

    inner class AddPeopleViewHolder(private val binding: ItemAddPeopleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            val context = itemView.context
            binding.tvName.text = contact.name
            
            if (contact.photoUri != null) {
                binding.tvInitialsFull.visibility = View.GONE
                binding.ivAvatarFull.visibility = View.VISIBLE
                binding.ivAvatarFull.tag = contact.photoUri
                binding.ivAvatarFull.imageTintList = null // Photo doesn't need tint
                binding.ivAvatarFull.backgroundTintList = null // Photo doesn't need background
                
                Glide.with(itemView.context)
                    .load(contact.photoUri)
                    .circleCrop()
                    .dontAnimate()
                    .into(binding.ivAvatarFull)
            } else {
                val initials = getInitials(contact.name ?: contact.phoneNumber)
                if (initials.isNotEmpty() && !initials[0].isDigit()) {
                    binding.tvInitialsFull.visibility = View.VISIBLE
                    binding.ivAvatarFull.visibility = View.GONE
                    binding.ivAvatarFull.tag = null
                    binding.tvInitialsFull.text = initials
                } else {
                    binding.tvInitialsFull.visibility = View.GONE
                    binding.ivAvatarFull.visibility = View.VISIBLE
                    if (binding.ivAvatarFull.tag != "person_fallback") {
                        binding.ivAvatarFull.tag = "person_fallback"
                        binding.ivAvatarFull.setImageResource(R.drawable.ic_person)
                        binding.ivAvatarFull.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.black))
                        binding.ivAvatarFull.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorChildBackgroundDark))
                    }
                }
            }

            val isSelected = selectedContacts.contains(contact.phoneNumber)
            binding.cbSelect.isChecked=isSelected
//            binding.cbSelect.setBackgroundResource(if (isSelected) R.drawable.ic_checked else R.drawable.ic_uncheck)

            binding.cbSelect.setOnClickListener {
                if (selectedContacts.contains(contact.phoneNumber)) {
                    selectedContacts.remove(contact.phoneNumber)
                } else {
                    selectedContacts.add(contact.phoneNumber)
                }
                notifyItemChanged(adapterPosition)
                onSelectionChanged(selectedContacts.size)
            }
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
