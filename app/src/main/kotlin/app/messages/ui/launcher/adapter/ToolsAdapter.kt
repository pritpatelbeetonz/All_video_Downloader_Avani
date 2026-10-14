package app.messages.ui.launcher.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.model.ToolModel

class ToolsAdapter(
    private val onToolClick: (ToolModel) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    private val tools = mutableListOf<ToolModel>()

    fun submitList(newTools: List<ToolModel>) {
        tools.clear()
        tools.addAll(newTools)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool_card, parent, false)
        return ToolViewHolder(view, onToolClick)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(tools[position])
    }

    override fun getItemCount(): Int = tools.size

    class ToolViewHolder(itemView: View, private val onToolClick: (ToolModel) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivToolIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvToolTitle)

        fun bind(tool: ToolModel) {
            tvTitle.text = tool.title
            
            Glide.with(itemView.context)
                .load(tool.image)
                .placeholder(android.R.drawable.sym_def_app_icon)
                .error(android.R.drawable.sym_def_app_icon)
                .into(ivIcon)

            itemView.setOnClickListener {
                onToolClick(tool)
            }
        }
    }
}
