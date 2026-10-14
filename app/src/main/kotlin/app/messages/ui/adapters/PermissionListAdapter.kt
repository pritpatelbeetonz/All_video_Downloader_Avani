package app.messages.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.messages.data.model.PermissionItem
import app.messages.databinding.RawPermissionListBinding


class PermissionListAdapter(
    private val permissionItem: List<PermissionItem>,
) : RecyclerView.Adapter<PermissionListAdapter.PermissionViewHolder>() {

    inner class PermissionViewHolder(val binding: RawPermissionListBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val binding =
            RawPermissionListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PermissionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        val permission = permissionItem[position]

        with(holder.binding) {
            val context = root.context

            ivPermission.setImageResource(permission.iconRes)
            tvPermissionName.text = permission.title
            tvPermissionDetails.text = permission.description
        }
    }

    override fun getItemCount(): Int = permissionItem.size
}