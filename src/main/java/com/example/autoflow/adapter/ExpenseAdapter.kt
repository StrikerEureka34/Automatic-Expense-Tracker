package com.example.autoflow.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.autoflow.R
import com.example.autoflow.model.Expense
import java.text.SimpleDateFormat
import java.util.*

class ExpenseAdapter(private var expenses: List<Expense>) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.expenseTitle)
        val amountText: TextView = itemView.findViewById(R.id.expenseAmount)
        val categoryText: TextView = itemView.findViewById(R.id.expenseCategory)
        val timestampText: TextView = itemView.findViewById(R.id.expenseTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]
        holder.titleText.text = expense.title
        holder.amountText.text = "₹${String.format("%.2f", expense.amount)}"
        holder.categoryText.text = expense.category

        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        holder.timestampText.text = dateFormat.format(expense.timestamp)
    }

    override fun getItemCount(): Int = expenses.size

    fun updateExpenses(newExpenses: List<Expense>) {
        expenses = newExpenses
        notifyDataSetChanged()
    }
}
