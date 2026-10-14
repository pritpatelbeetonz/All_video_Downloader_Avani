package app.messages.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.messages.data.model.LanguageModel
import app.messages.databinding.ItemLanguageBinding

class LanguageAdapter : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

    private val items = mutableListOf<LanguageModel>()

    fun submitList(list: List<LanguageModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun hasSelection(): Boolean {
        return items.any { it.isSelected }
    }

    fun getSelectedLanguage(): LanguageModel? {
        return items.find { it.isSelected }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LanguageModel, position: Int) {

            binding.tvCountry.text = item.name

            if (item.isSelected) {
                binding.rootLanguageItem.setBackgroundResource(app.messages.R.drawable.bg_language_item_selected)
            } else {
                binding.rootLanguageItem.setBackgroundResource(app.messages.R.drawable.bg_language_item_default)
            }
            binding.tvCountry.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, app.messages.R.color.text_primary))

            binding.rootLanguageItem.setOnClickListener {
                for (index in items.indices) {
                    items[index].isSelected = false
                }
                items[position].isSelected = true
                notifyDataSetChanged()
            }

        }
    }
}