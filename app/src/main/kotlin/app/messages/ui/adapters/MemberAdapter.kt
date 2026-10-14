package app.messages.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.db.Contact
import app.messages.databinding.ItemAddPeopleBinding

class MemberAdapter : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    private val members = ArrayList<MemberItem>()

    data class MemberItem(
        val contact: Contact,
        val isOwner: Boolean = false,
        val isAdmin: Boolean = false
    )

    fun submitList(list: List<MemberItem>) {
        members.clear()
        members.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemAddPeopleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount() = members.size

    inner class MemberViewHolder(private val binding: ItemAddPeopleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MemberItem) {
            val contact = item.contact
            binding.tvName.text = contact.name
            
            Glide.with(itemView.context)
                .load(contact.photoUri)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivAvatarFull)

            // Hide checkbox as this is only for viewing
            binding.cbSelect.visibility = View.GONE

            // Show Owner/Admin labels
            binding.txtOwner.visibility = if (item.isOwner) View.VISIBLE else View.GONE
            binding.txtAdmin.visibility = if (item.isAdmin) View.VISIBLE else View.GONE
        }
    }
}
