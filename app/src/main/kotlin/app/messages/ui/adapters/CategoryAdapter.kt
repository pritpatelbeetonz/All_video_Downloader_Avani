package app.messages.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.messages.R
import app.messages.databinding.ItemCategoryChipBinding

class CategoryAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = 0

    inner class CategoryViewHolder(private val binding: ItemCategoryChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, position: Int) {
            binding.tvCategoryName.text = category
            if (position == selectedPosition) {
                binding.tvCategoryName.setBackgroundResource(R.drawable.bg_category_chip_selected)
                binding.tvCategoryName.setTextColor(Color.WHITE)
            } else {
                binding.tvCategoryName.setBackgroundResource(R.drawable.rounded_grey_border_10)
                binding.tvCategoryName.setTextColor(Color.parseColor("#94A3B8"))
            }

            binding.root.setOnClickListener {
                if (selectedPosition == position) return@setOnClickListener
                val previous = selectedPosition
                selectedPosition = position
                
                notifyItemChanged(previous)
                notifyItemChanged(selectedPosition)
                onCategorySelected(category)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount(): Int = categories.size
}
